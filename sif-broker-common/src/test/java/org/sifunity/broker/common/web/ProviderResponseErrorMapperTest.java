// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.core.io.buffer.DataBufferLimitException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProviderResponseErrorMapper}, the shared decision logic
 * for mapping upstream response read/decode failures to client-facing errors.
 */
@DisplayName("ProviderResponseErrorMapper")
class ProviderResponseErrorMapperTest {

    @Nested
    @DisplayName("isNonErrorStatus")
    class IsNonErrorStatus {

        @Test
        @DisplayName("returns true for 2xx statuses")
        void trueFor2xx() {
            assertThat(ProviderResponseErrorMapper.isNonErrorStatus(200)).isTrue();
            assertThat(ProviderResponseErrorMapper.isNonErrorStatus(204)).isTrue();
        }

        @Test
        @DisplayName("returns true for 3xx statuses")
        void trueFor3xx() {
            assertThat(ProviderResponseErrorMapper.isNonErrorStatus(301)).isTrue();
        }

        @Test
        @DisplayName("returns false for 4xx and 5xx statuses")
        void falseForErrors() {
            assertThat(ProviderResponseErrorMapper.isNonErrorStatus(400)).isFalse();
            assertThat(ProviderResponseErrorMapper.isNonErrorStatus(404)).isFalse();
            assertThat(ProviderResponseErrorMapper.isNonErrorStatus(500)).isFalse();
            assertThat(ProviderResponseErrorMapper.isNonErrorStatus(503)).isFalse();
        }
    }

    @Nested
    @DisplayName("isResponseTooLarge")
    class IsResponseTooLarge {

        @Test
        @DisplayName("returns true when the throwable is a DataBufferLimitException")
        void directMatch() {
            Throwable ex = new DataBufferLimitException("Exceeded limit on max bytes to buffer : 262144");
            assertThat(ProviderResponseErrorMapper.isResponseTooLarge(ex)).isTrue();
        }

        @Test
        @DisplayName("returns true when a DataBufferLimitException is nested in the cause chain")
        void nestedMatch() {
            Throwable root = new DataBufferLimitException("too big");
            Throwable wrapped = new RuntimeException("decode failed", new IllegalStateException("x", root));
            assertThat(ProviderResponseErrorMapper.isResponseTooLarge(wrapped)).isTrue();
        }

        @Test
        @DisplayName("returns false when no DataBufferLimitException is present")
        void noMatch() {
            Throwable ex = new RuntimeException("some other failure", new IllegalStateException("nope"));
            assertThat(ProviderResponseErrorMapper.isResponseTooLarge(ex)).isFalse();
        }

        @Test
        @DisplayName("returns false for null")
        void nullSafe() {
            assertThat(ProviderResponseErrorMapper.isResponseTooLarge(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("codeForProcessingFailure")
    class CodeForProcessingFailure {

        @Test
        @DisplayName("returns TOO_LARGE_CODE for a buffer overflow")
        void tooLarge() {
            Throwable ex = new DataBufferLimitException("too big");
            assertThat(ProviderResponseErrorMapper.codeForProcessingFailure(ex))
                    .isEqualTo(ProviderResponseErrorMapper.TOO_LARGE_CODE);
        }

        @Test
        @DisplayName("returns UNPROCESSABLE_CODE for other decode failures")
        void unprocessable() {
            Throwable ex = new RuntimeException("bad json");
            assertThat(ProviderResponseErrorMapper.codeForProcessingFailure(ex))
                    .isEqualTo(ProviderResponseErrorMapper.UNPROCESSABLE_CODE);
        }
    }

    @Nested
    @DisplayName("messageForProcessingFailure")
    class MessageForProcessingFailure {

        @Test
        @DisplayName("mentions reducing page size for a buffer overflow")
        void tooLargeMessage() {
            Throwable ex = new DataBufferLimitException("too big");
            assertThat(ProviderResponseErrorMapper.messageForProcessingFailure(ex))
                    .contains("exceeded the maximum size")
                    .contains("navigationPageSize");
        }

        @Test
        @DisplayName("gives a generic decode message otherwise")
        void genericMessage() {
            Throwable ex = new RuntimeException("bad json");
            assertThat(ProviderResponseErrorMapper.messageForProcessingFailure(ex))
                    .contains("could not read or decode");
        }
    }
}
