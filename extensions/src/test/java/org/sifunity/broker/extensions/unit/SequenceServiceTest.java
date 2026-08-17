package org.sifunity.broker.extensions.unit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sifunity.broker.extensions.sequencing.SequenceService;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SequenceService Unit Tests")
class SequenceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SequenceService sequenceService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        sequenceService = new SequenceService(redisTemplate);
    }

    @Test
    @DisplayName("nextSequence returns incremented value from Redis")
    void nextSequence_returnsIncrementedValue() {
        String key = "domain:testDomain:solution:testSolution:messagesequence";
        when(valueOperations.increment(key)).thenReturn(42L);

        long result = sequenceService.nextSequence("testDomain", "testSolution");

        assertThat(result).isEqualTo(42L);
    }

    @Test
    @DisplayName("nextSequence returns zero when Redis returns null")
    void nextSequence_returnsZeroWhenRedisReturnsNull() {
        String key = "domain:domainA:solution:solutionB:messagesequence";
        when(valueOperations.increment(key)).thenReturn(null);

        long result = sequenceService.nextSequence("domainA", "solutionB");

        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("nextSequence uses correct Redis key pattern")
    void nextSequence_usesCorrectKey() {
        String expectedKey = "domain:myDomain:solution:mySolution:messagesequence";
        when(valueOperations.increment(expectedKey)).thenReturn(1L);

        sequenceService.nextSequence("myDomain", "mySolution");

        verify(valueOperations).increment(expectedKey);
    }

    @Test
    @DisplayName("nextSequence propagates RedisConnectionFailureException")
    void nextSequence_propagatesRedisException() {
        String key = "domain:d1:solution:s1:messagesequence";
        when(valueOperations.increment(key))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        assertThatThrownBy(() -> sequenceService.nextSequence("d1", "s1"))
                .isInstanceOf(RedisConnectionFailureException.class)
                .hasMessageContaining("Connection refused");
    }
}
