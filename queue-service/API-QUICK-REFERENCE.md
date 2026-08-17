# Queue Service - API Quick Reference

Quick reference for developers integrating with the Queue Service.

## Base URL

```
http://localhost:8082  (local)
http://queue-service:8082  (Kubernetes)
```

## Authentication

The Queue Service expects requests to come through the Gateway service, which handles authentication. Direct calls should include environment context in headers.

---

## Queue Management

### Create Queue

```http
POST /api/queues
Content-Type: application/json

{
  "name": "consumer-responses",
  "ownerId": "550e8400-e29b-41d4-a716-446655440000",
  "maxRetries": 3,
  "retryDelayMs": 5000
}
```

**Response: 201 Created**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "name": "consumer-responses",
  "ownerId": "550e8400-e29b-41d4-a716-446655440000",
  "createdAt": "2024-01-15T10:30:00Z",
  "maxRetries": 3,
  "retryDelayMs": 5000,
  "messageCount": 0
}
```

### Get Queue

```http
GET /api/queues/{queueId}
```

**Response: 200 OK** (same as create response)

### List Queues by Owner

```http
GET /api/queues?ownerId={environmentId}
```

**Response: 200 OK**
```json
[
  {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "name": "consumer-responses",
    "ownerId": "550e8400-e29b-41d4-a716-446655440000",
    "createdAt": "2024-01-15T10:30:00Z",
    "maxRetries": 3,
    "retryDelayMs": 5000,
    "messageCount": 42
  }
]
```

### Delete Queue

```http
DELETE /api/queues/{queueId}
```

**Response: 204 No Content**

### Get Queue Statistics

```http
GET /api/queues/{queueId}/stats
```

**Response: 200 OK**
```json
{
  "queueId": "123e4567-e89b-12d3-a456-426614174000",
  "pendingCount": 42,
  "deliveredCount": 5,
  "deadLetterCount": 2,
  "totalProcessed": 49
}
```

---

## Message Operations

### Enqueue Message

```http
POST /api/queues/{queueId}/messages
Content-Type: application/json

{
  "messageId": "optional-uuid",
  "environment": "prod-consumer-1",
  "zone": "default",
  "context": "district-a",
  "service": "StudentPersonal",
  "payload": {
    "type": "RESPONSE",
    "data": { ... }
  }
}
```

**Response: 201 Created**
```json
{
  "messageId": "789e4567-e89b-12d3-a456-426614174000",
  "queueId": "123e4567-e89b-12d3-a456-426614174000",
  "storageKey": "prod-consumer-1/default/district-a/StudentPersonal/789e4567-e89b-12d3-a456-426614174000.json",
  "enqueuedAt": "2024-01-15T10:30:00Z",
  "status": "PENDING"
}
```

### Deliver Messages (Poll)

```http
POST /api/queues/{queueId}/deliver
Content-Type: application/json

{
  "maxMessages": 10,
  "visibilityTimeoutSeconds": 30
}
```

**Response: 200 OK**
```json
{
  "messages": [
    {
      "messageId": "789e4567-e89b-12d3-a456-426614174000",
      "queueId": "123e4567-e89b-12d3-a456-426614174000",
      "storageKey": "prod-consumer-1/default/district-a/StudentPersonal/789e4567-e89b-12d3-a456-426614174000.json",
      "enqueuedAt": "2024-01-15T10:30:00Z",
      "deliveredAt": "2024-01-15T10:31:00Z",
      "deliveryAttempts": 1,
      "status": "DELIVERED",
      "payload": {
        "type": "RESPONSE",
        "data": { ... }
      }
    }
  ]
}
```

### Acknowledge Message

```http
POST /api/messages/{messageId}/acknowledge
```

**Response: 200 OK**
```json
{
  "messageId": "789e4567-e89b-12d3-a456-426614174000",
  "status": "ACKNOWLEDGED",
  "acknowledgedAt": "2024-01-15T10:31:30Z"
}
```

---

## Dead-Letter Queue

### Get Dead-Letter Messages

```http
GET /api/queues/{queueId}/dead-letter
```

**Response: 200 OK**
```json
{
  "messages": [
    {
      "messageId": "999e4567-e89b-12d3-a456-426614174000",
      "queueId": "123e4567-e89b-12d3-a456-426614174000",
      "storageKey": "...",
      "enqueuedAt": "2024-01-15T10:30:00Z",
      "deliveredAt": "2024-01-15T10:35:00Z",
      "deliveryAttempts": 3,
      "status": "FAILED",
      "payload": { ... }
    }
  ]
}
```

### Reprocess Dead-Letter Message

```http
POST /api/messages/{messageId}/reprocess
```

**Response: 200 OK** (empty body)

---

## Error Responses

All errors follow the SIF error response format:

```json
{
  "code": "ROUTE_002",
  "message": "Queue not found: 123e4567-e89b-12d3-a456-426614174000",
  "scope": "BROKER",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Common Error Codes

| Code | Status | Description |
|------|--------|-------------|
| `ROUTE_002` | 404 | Queue not found |
| `VAL_001` | 400 | Validation failed |
| `VAL_002` | 400 | Invalid argument |
| `STORAGE_001` | 500 | Storage operation failed |
| `SYS_001` | 500 | Internal server error |

### HTTP Status Codes

- `200 OK` - Successful operation
- `201 Created` - Resource created
- `204 No Content` - Successful deletion
- `400 Bad Request` - Invalid input
- `404 Not Found` - Resource not found
- `500 Internal Server Error` - Server error
- `503 Service Unavailable` - Service unavailable

---

## Usage Examples

### Complete Message Lifecycle

```bash
# 1. Create a queue
QUEUE_ID=$(curl -X POST http://localhost:8082/api/queues \
  -H "Content-Type: application/json" \
  -d '{
    "name": "test-queue",
    "ownerId": "550e8400-e29b-41d4-a716-446655440000",
    "maxRetries": 3,
    "retryDelayMs": 5000
  }' | jq -r '.id')

# 2. Enqueue a message
MESSAGE_ID=$(curl -X POST http://localhost:8082/api/queues/$QUEUE_ID/messages \
  -H "Content-Type: application/json" \
  -d '{
    "environment": "test",
    "zone": "default",
    "context": "test-context",
    "service": "TestService",
    "payload": {"data": "test"}
  }' | jq -r '.messageId')

# 3. Deliver messages
curl -X POST http://localhost:8082/api/queues/$QUEUE_ID/deliver \
  -H "Content-Type: application/json" \
  -d '{
    "maxMessages": 10,
    "visibilityTimeoutSeconds": 30
  }'

# 4. Acknowledge message
curl -X POST http://localhost:8082/api/messages/$MESSAGE_ID/acknowledge

# 5. Check queue statistics
curl http://localhost:8082/api/queues/$QUEUE_ID/stats

# 6. Delete queue
curl -X DELETE http://localhost:8082/api/queues/$QUEUE_ID
```

### Polling Pattern

```javascript
// JavaScript/Node.js example
async function pollMessages(queueId) {
  while (true) {
    try {
      // Poll for messages
      const response = await fetch(
        `http://localhost:8082/api/queues/${queueId}/deliver`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            maxMessages: 10,
            visibilityTimeoutSeconds: 30
          })
        }
      );
      
      const { messages } = await response.json();
      
      // Process each message
      for (const message of messages) {
        try {
          await processMessage(message.payload);
          
          // Acknowledge successful processing
          await fetch(
            `http://localhost:8082/api/messages/${message.messageId}/acknowledge`,
            { method: 'POST' }
          );
        } catch (error) {
          console.error('Failed to process message:', error);
          // Message will be redelivered after visibility timeout
        }
      }
      
      // Wait before next poll if no messages
      if (messages.length === 0) {
        await sleep(5000);
      }
    } catch (error) {
      console.error('Polling error:', error);
      await sleep(10000);
    }
  }
}
```

### Java Client Example

```java
// Java example using RestTemplate
@Service
public class QueueServiceClient {
    
    private final RestTemplate restTemplate;
    private final String baseUrl = "http://queue-service:8082";
    
    public QueueDTO createQueue(String name, UUID ownerId) {
        CreateQueueRequest request = new CreateQueueRequest(
            name, ownerId, 3, 5000L
        );
        
        return restTemplate.postForObject(
            baseUrl + "/api/queues",
            request,
            QueueDTO.class
        );
    }
    
    public void enqueueMessage(UUID queueId, Object payload) {
        EnqueueMessageRequest request = new EnqueueMessageRequest(
            null, "prod", "default", "context", "service", payload
        );
        
        restTemplate.postForObject(
            baseUrl + "/api/queues/" + queueId + "/messages",
            request,
            EnqueueMessageResponse.class
        );
    }
    
    public List<MessageResponse> deliverMessages(UUID queueId, int maxMessages) {
        DeliverMessagesRequest request = new DeliverMessagesRequest(
            maxMessages, 30
        );
        
        DeliverMessagesResponse response = restTemplate.postForObject(
            baseUrl + "/api/queues/" + queueId + "/deliver",
            request,
            DeliverMessagesResponse.class
        );
        
        return response.messages();
    }
    
    public void acknowledgeMessage(UUID messageId) {
        restTemplate.postForObject(
            baseUrl + "/api/messages/" + messageId + "/acknowledge",
            null,
            AcknowledgeMessageResponse.class
        );
    }
}
```

---

## Best Practices

### Queue Creation
- Use descriptive queue names
- Set appropriate maxRetries based on message importance
- Consider retryDelayMs based on expected failure recovery time

### Message Enqueuing
- Include all required context (environment, zone, service)
- Keep payloads under 10MB
- Use meaningful message IDs if provided

### Message Delivery
- Poll with reasonable maxMessages (10-50)
- Set visibilityTimeout longer than expected processing time
- Handle empty responses gracefully

### Message Acknowledgment
- Always acknowledge successfully processed messages
- Don't acknowledge if processing fails (let it retry)
- Acknowledgment is idempotent (safe to retry)

### Error Handling
- Implement exponential backoff for polling
- Monitor dead-letter queues
- Log all errors with message IDs
- Reprocess DLQ messages after fixing issues

### Performance
- Use connection pooling for HTTP clients
- Poll multiple messages at once
- Process messages in parallel when possible
- Monitor queue statistics regularly

---

## Health & Monitoring

### Health Check
```bash
curl http://localhost:8082/actuator/health
```

### Metrics
```bash
curl http://localhost:8082/actuator/metrics
curl http://localhost:8082/actuator/prometheus
```

### Queue Statistics
```bash
curl http://localhost:8082/api/queues/{queueId}/stats
```

---

## Support

- **Documentation**: See README.md, DEPLOYMENT-GUIDE.md
- **Issues**: Check logs at `/actuator/logfile`
- **Metrics**: Monitor via `/actuator/prometheus`
- **Health**: Check `/actuator/health`
