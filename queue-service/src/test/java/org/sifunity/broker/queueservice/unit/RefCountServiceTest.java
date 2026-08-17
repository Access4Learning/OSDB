// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.queueservice.redis.RedisKeyManager;
import org.sifunity.broker.queueservice.service.RefCountService;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RefCountService.
 *
 * Tests ref count key generation, increment via INCR, and
 * atomic decrement-and-conditional-delete via Lua script.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RefCountService Unit Tests")
class RefCountServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RefCountService refCountService;

    @BeforeEach
    void setUp() {
        refCountService = new RefCountService(redisTemplate, new RedisKeyManager());
    }

    @Nested
    @DisplayName("refCountKey()")
    class RefCountKeyTests {

        @Test
        @DisplayName("should generate correct key format")
        void shouldGenerateCorrectKeyFormat() {
            String key = refCountService.refCountKey("state-a-doe", "production", "some/storage/key.json");

            assertThat(key).isEqualTo(
                    "domain:state-a-doe:solution:production:ref:some/storage/key.json"
            );
        }

        @Test
        @DisplayName("should handle different domain and solution values")
        void shouldHandleDifferentValues() {
            String key = refCountService.refCountKey("district-b", "staging", "path/to/payload.json");

            assertThat(key).isEqualTo(
                    "domain:district-b:solution:staging:ref:path/to/payload.json"
            );
        }
    }

    @Nested
    @DisplayName("increment()")
    class IncrementTests {

        @Test
        @DisplayName("should call Redis INCR and return new count")
        void shouldCallIncrAndReturnCount() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("ref-key")).thenReturn(3L);

            long result = refCountService.increment("ref-key");

            assertThat(result).isEqualTo(3L);
            verify(valueOperations).increment("ref-key");
        }

        @Test
        @DisplayName("should return 0 when INCR returns null")
        void shouldReturnZeroWhenNull() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("ref-key")).thenReturn(null);

            long result = refCountService.increment("ref-key");

            assertThat(result).isEqualTo(0L);
        }

        @Test
        @DisplayName("should return 1 for first increment on new key")
        void shouldReturnOneForFirstIncrement() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.increment("new-key")).thenReturn(1L);

            long result = refCountService.increment("new-key");

            assertThat(result).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("decrementAndGet()")
    class DecrementAndGetTests {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should execute Lua script and return new count")
        void shouldExecuteLuaScriptAndReturnCount() {
            when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenReturn(2L);

            long result = refCountService.decrementAndGet("ref-key");

            assertThat(result).isEqualTo(2L);
            verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("ref-key")));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should return 0 when count reaches zero (payload should be deleted)")
        void shouldReturnZeroWhenCountReachesZero() {
            when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenReturn(0L);

            long result = refCountService.decrementAndGet("ref-key");

            assertThat(result).isEqualTo(0L);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should return 0 when script returns null")
        void shouldReturnZeroWhenNull() {
            when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenReturn(null);

            long result = refCountService.decrementAndGet("ref-key");

            assertThat(result).isEqualTo(0L);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Test
        @DisplayName("should pass correct key to Lua script")
        void shouldPassCorrectKeyToScript() {
            String refKey = "domain:test:solution:prod:ref:key.json";
            when(redisTemplate.execute(any(RedisScript.class), any(List.class))).thenReturn(1L);

            refCountService.decrementAndGet(refKey);

            ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
            verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture());
            assertThat(keysCaptor.getValue()).containsExactly(refKey);
        }
    }

    @Nested
    @DisplayName("decrementBatch()")
    class DecrementBatchTests {

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should return correct counts for multiple keys via pipeline")
        void shouldReturnCorrectCountsForMultipleKeys() {
            List<String> refKeys = List.of("key-a", "key-b", "key-c");
            List<Object> pipelineResults = List.of(2L, 0L, 5L);

            when(redisTemplate.executePipelined(any(SessionCallback.class))).thenReturn(pipelineResults);

            Map<String, Long> result = refCountService.decrementBatch(refKeys);

            assertThat(result).hasSize(3);
            assertThat(result.get("key-a")).isEqualTo(2L);
            assertThat(result.get("key-b")).isEqualTo(0L);
            assertThat(result.get("key-c")).isEqualTo(5L);
            verify(redisTemplate).executePipelined(any(SessionCallback.class));
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should treat null pipeline results as 0")
        void shouldTreatNullResultsAsZero() {
            List<String> refKeys = List.of("key-a", "key-b");
            List<Object> pipelineResults = new java.util.ArrayList<>();
            pipelineResults.add(3L);
            pipelineResults.add(null);

            when(redisTemplate.executePipelined(any(SessionCallback.class))).thenReturn(pipelineResults);

            Map<String, Long> result = refCountService.decrementBatch(refKeys);

            assertThat(result.get("key-a")).isEqualTo(3L);
            assertThat(result.get("key-b")).isEqualTo(0L);
        }

        @Test
        @DisplayName("should return empty map for empty input list")
        void shouldReturnEmptyMapForEmptyInput() {
            Map<String, Long> result = refCountService.decrementBatch(Collections.emptyList());

            assertThat(result).isEmpty();
            verifyNoInteractions(redisTemplate);
        }

        @Test
        @DisplayName("should return empty map for null input")
        void shouldReturnEmptyMapForNullInput() {
            Map<String, Long> result = refCountService.decrementBatch(null);

            assertThat(result).isEmpty();
            verifyNoInteractions(redisTemplate);
        }

        @SuppressWarnings("unchecked")
        @Test
        @DisplayName("should report keys reaching zero (deleted by Lua script)")
        void shouldReportKeysReachingZero() {
            List<String> refKeys = List.of("shared-key", "unique-key");
            // shared-key still has refs, unique-key reached zero (Lua deleted it)
            List<Object> pipelineResults = List.of(1L, 0L);

            when(redisTemplate.executePipelined(any(SessionCallback.class))).thenReturn(pipelineResults);

            Map<String, Long> result = refCountService.decrementBatch(refKeys);

            assertThat(result.get("shared-key")).isEqualTo(1L);
            assertThat(result.get("unique-key")).isEqualTo(0L);
            // Caller can filter for entries where count <= 0 to know which payloads to delete
        }
    }
}
