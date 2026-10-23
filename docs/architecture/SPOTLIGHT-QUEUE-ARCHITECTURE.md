# SPOTLIGHT: Queue Architecture — Redis Streams + Pluggable Storage

How the broker delivers messages to consumers using Redis Streams as pointers and a separate storage backend for payloads.

---

## Core Design Principle

**Separate the pointer from the payload.**

Redis Streams handle ordering, consumer groups, acknowledgment, and redelivery. The actual message payload lives in pluggable storage (filesystem or S3). This split gives us:

- **Efficiency**: Fan-out to N consumers without copying the payload N times
- **Flexibility**: Swap storage backends without touching queue logic
- **Scalability**: Redis stays lean (small pointer entries), storage handles large payloads

---

## How It Works

```
Producer (events-connector, responses-connector)
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│  Queue Service: MessageService.enqueueMessage()         │
│                                                         │
│  1. Generate storageKey (domain/solution/env/zone/...)  │
│  2. Increment ref count for storageKey                  │
│  3. Store payload → StorageProvider, but only when      │
│     this was the first reference (fan-out skips it)     │
│  4. XADD to Redis Stream (pointer only: messageId,      │
│     storageKey, enqueuedAt, sifHeaders)                 │
└─────────────────────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│  Redis Stream                                           │
│  Key: see "Redis Key Formats" below                     │
│                                                         │
│  Entry fields (small — no payload):                     │
│    • messageId (UUID)                                   │
│    • storageKey (path to payload)                       │
│    • enqueuedAt (ISO timestamp)                         │
│    • sifHeaders (JSON map of passthrough headers)       │
└─────────────────────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│  Consumer polls: GET /queues/{queueId}/messages         │
│                                                         │
│  1. XREADGROUP from stream (gets pointer, not payload)  │
│  2. Retrieve payload from StorageProvider via storageKey│
│  3. Return payload + messageid header to consumer       │
│  4. Message stays in PEL until consumer ACKs            │
└─────────────────────────────────────────────────────────┘
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│  Consumer ACKs: GET with deleteMessageId header         │
│                                                         │
│  1. XACK removes from PEL                               │
│  2. Decrement ref count for storageKey                  │
│  3. If ref count reaches 0 → delete payload from store  │
└─────────────────────────────────────────────────────────┘
```

---

## Fan-Out: One Payload, Many Consumers

When an event targets multiple subscribers (e.g., 5 consumer environments subscribe to `StudentPersonal` events):

```
Event arrives
  │
  ▼
Store payload ONCE → storageKey = "domain/solution/.../event/StudentPersonal/{msgId}.json"
Ref count reaches 5 (INCR once per target queue)
  │
  ├─ XADD to consumer-1's stream (pointer → storageKey)
  ├─ XADD to consumer-2's stream (pointer → storageKey)
  ├─ XADD to consumer-3's stream (pointer → storageKey)
  ├─ XADD to consumer-4's stream (pointer → storageKey)
  └─ XADD to consumer-5's stream (pointer → storageKey)
```

Each consumer gets their own stream entry (for independent ACK tracking), but all point to the **same** stored payload. No duplication.

As consumers ACK:
- Consumer-1 ACKs → ref count = 4
- Consumer-3 ACKs → ref count = 3
- ... eventually ...
- Consumer-5 ACKs → ref count = 0 → payload deleted from storage

---

## Consumer Groups and Multiple Connections

Per SIF spec, a single consumer environment can open multiple concurrent connections to the same queue (e.g., for parallel processing). Redis Consumer Groups handle this natively:

| Concept | Redis Mechanism | SIF Mapping |
|---------|----------------|-------------|
| Queue | Stream | One per consumer environment per subscription |
| Consumer agent | Consumer in group | `connectionId` header from SIF client |
| Delivery tracking | PEL (Pending Entries List) | Ensures no double-delivery across connections |
| Acknowledgment | XACK | `deleteMessageId` on next GET |
| Redelivery | XCLAIM after visibility timeout | Handles crashed consumers |

A consumer with `connectionId=agent-A` and `connectionId=agent-B` hitting the same queue get different messages — Redis guarantees each message is delivered to exactly one consumer within the group.

---

## Pluggable Storage Backend

```java
public interface StorageProvider {
    String store(String storageKey, Object payload) throws StorageException;
    Optional<Object> retrieve(String storageKey) throws StorageException;
    void delete(String storageKey) throws StorageException;
    boolean exists(String storageKey) throws StorageException;

    /** Batch delete. The default implementation deletes sequentially and logs
     *  individual failures rather than aborting the batch. */
    default void deleteBatch(List<String> storageKeys) { ... }

    /** Prefix delete, used for domain and solution cascade cleanup.
     *  Returns the number of objects removed. */
    default long deleteByPrefix(String prefix) { ... }
}
```

| Backend | Config | Use Case |
|---------|--------|----------|
| `FileSystemStorageProvider` | `STORAGE_TYPE=filesystem` | Local dev, testing |
| `S3StorageProvider` | `STORAGE_TYPE=s3` | Production (durable, scalable) |

Selection is config-driven — no code changes needed to switch.

---

## Storage Key Format

```
[{prefix}/]{domainId}/{solutionId}/{environmentId}/{zoneId}/{contextId}/{messageType}/{serviceName}/{messageId}.json
```

The leading {prefix} is optional and comes from `message.storage.key-prefix`; when it is
unset the key starts at {domainId}. Every segment is sanitised — any character outside
`[a-zA-Z0-9-_]` becomes an underscore.

Example:
```
StateEducation/Production/abc123-env/district-1/DEFAULT/event/StudentPersonal/550e8400-e29b-41d4-a716-446655440000.json
```

This hierarchical key enables:
- Easy debugging (browse filesystem by domain/solution)
- S3 prefix-based lifecycle policies
- Clear ownership and routing context

---

## Ref Count Lifecycle

```
Redis key: domain:{domainId}:solution:{solutionId}:ref:{storageKey}

INCR on enqueue (once per target queue)
DECR on ACK
DELETE payload when count reaches 0
```

This is the mechanism that makes fan-out efficient — the payload exists exactly once regardless of how many consumers need it.

---

## Redis Key Formats

Useful when inspecting a running broker with `redis-cli`.

| Purpose | Key |
|---------|-----|
| Message stream | `domain:{domainId}:solution:{solutionId}:function:msg_queue:env:{environmentId}:queue:{queueId}` |
| Dead letter stream | the message stream key, suffixed `:dlq` |
| Consumer group | `group:{queueId}` |
| Payload ref count | `domain:{domainId}:solution:{solutionId}:ref:{storageKey}` |

Every key is built in one place, so the domain/solution prefix stays consistent across
streams, groups and counters. That consistency is what makes per-tenant `SCAN` patterns
and cascade cleanup possible.

---

## Failure Handling

| Scenario | Mechanism |
|----------|-----------|
| Consumer crashes mid-processing | Visibility timeout → XCLAIM → redeliver to another connection |
| Max redelivery attempts exceeded | Escalate to Dead Letter Queue (DLQ) stream |
| Storage backend unavailable | Enqueue fails with 503; producer retries |
| Redis unavailable | Queue service health check fails; gateway stops routing |

---

## Why Not Store Payloads in Redis?

- **Memory cost**: Redis is RAM-based. Large SIF payloads (student records, assessment results) would exhaust memory quickly at scale.
- **Persistence guarantees**: S3 provides 11 nines durability. Redis persistence (RDB/AOF) is best-effort.
- **Fan-out efficiency**: Storing the payload once in S3 and referencing it N times in Redis is cheaper than storing N copies in Redis.
- **Flexibility**: Storage lifecycle (TTL, archival) is independent of queue mechanics.
