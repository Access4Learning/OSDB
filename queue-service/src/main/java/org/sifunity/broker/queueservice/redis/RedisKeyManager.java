package org.sifunity.broker.queueservice.redis;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Centralized Redis key generation utility.
 * All key construction for streams, DLQs, ref counts, rate limits,
 * and SCAN patterns is consolidated here.
 */
@Component
public class RedisKeyManager {

    // ── Stream keys ──

    public String streamKey(String domainId, String solutionId, UUID environmentId, UUID queueId) {
        return "domain:" + domainId + ":solution:" + solutionId
                + ":function:msg_queue:env:" + environmentId + ":queue:" + queueId;
    }

    public String dlqStreamKey(String domainId, String solutionId, UUID environmentId, UUID queueId) {
        return streamKey(domainId, solutionId, environmentId, queueId) + ":dlq";
    }

    // ── Consumer group (unchanged) ──

    public String groupName(UUID queueId) {
        return "group:" + queueId;
    }

    // ── Ref count keys ──

    public String refCountKey(String domainId, String solutionId, String storageKey) {
        return "domain:" + domainId + ":solution:" + solutionId + ":ref:" + storageKey;
    }

    // ── Rate limit keys ──

    public String rateLimitKey(String domainId, String solutionId, String environmentId) {
        return "domain:" + domainId + ":solution:" + solutionId
                + ":function:rate_limit:env:" + environmentId;
    }

    // ── SCAN patterns ──

    public String domainPattern(String domainId) {
        return "domain:" + domainId + ":*";
    }

    public String solutionPattern(String domainId, String solutionId) {
        return "domain:" + domainId + ":solution:" + solutionId + ":*";
    }

    // ── Legacy patterns (for backward-compat cleanup) ──

    public String legacyDomainPattern(String domainId) {
        return "function:msg_queue:domain:" + domainId + ":*";
    }

    public String legacySolutionPattern(String domainId, String solutionId) {
        return "function:msg_queue:domain:" + domainId + ":solution:" + solutionId + ":*";
    }

    // ── Legacy key generation (for migration-period reads) ──

    public String legacyStreamKey(String domainId, String solutionId, UUID environmentId, UUID queueId) {
        return "function:msg_queue:domain:" + domainId + ":solution:" + solutionId
                + ":environment:" + environmentId + ":queue:" + queueId;
    }

    public String legacyRefCountKey(String domainId, String solutionId, String storageKey) {
        return "function:msg_queue:domain:" + domainId + ":solution:" + solutionId
                + ":payload-ref:" + storageKey;
    }
}
