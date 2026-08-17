// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.util;

import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.exception.ParameterConflictException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ParameterResolver}.
 *
 * Validates Requirements 2.1, 2.2, 2.3, 2.4, 2.5, 2.6.
 */
class ParameterResolverTest {

    @Test
    void bothAbsent_returnsAllNull() {
        var result = ParameterResolver.resolve(Map.of(), Map.of());

        assertThat(result.zoneId()).isNull();
        assertThat(result.contextId()).isNull();
        assertThat(result.deleteMessageId()).isNull();
    }

    @Test
    void nullInputs_returnsAllNull() {
        var result = ParameterResolver.resolve(null, null);

        assertThat(result.zoneId()).isNull();
        assertThat(result.contextId()).isNull();
        assertThat(result.deleteMessageId()).isNull();
    }

    @Test
    void matrixOnly_usesMatrixValues() {
        var matrix = Map.of("zoneId", "zone1", "contextId", "ctx1", "messageId", "msg1");
        var result = ParameterResolver.resolve(matrix, Map.of());

        assertThat(result.zoneId()).isEqualTo("zone1");
        assertThat(result.contextId()).isEqualTo("ctx1");
        assertThat(result.deleteMessageId()).isEqualTo("msg1");
    }

    @Test
    void headerOnly_usesHeaderValues() {
        var headers = Map.of("zoneId", "zone2", "contextId", "ctx2", "deleteMessageId", "msg2");
        var result = ParameterResolver.resolve(Map.of(), headers);

        assertThat(result.zoneId()).isEqualTo("zone2");
        assertThat(result.contextId()).isEqualTo("ctx2");
        assertThat(result.deleteMessageId()).isEqualTo("msg2");
    }

    @Test
    void bothSameValue_usesSharedValue() {
        var matrix = Map.of("zoneId", "zone1", "contextId", "ctx1", "messageId", "msg1");
        var headers = Map.of("zoneId", "zone1", "contextId", "ctx1", "deleteMessageId", "msg1");
        var result = ParameterResolver.resolve(matrix, headers);

        assertThat(result.zoneId()).isEqualTo("zone1");
        assertThat(result.contextId()).isEqualTo("ctx1");
        assertThat(result.deleteMessageId()).isEqualTo("msg1");
    }

    @Test
    void zoneIdConflict_throwsException() {
        var matrix = Map.of("zoneId", "zone-matrix");
        var headers = Map.of("zoneId", "zone-header");

        assertThatThrownBy(() -> ParameterResolver.resolve(matrix, headers))
                .isInstanceOf(ParameterConflictException.class)
                .satisfies(ex -> {
                    var pce = (ParameterConflictException) ex;
                    assertThat(pce.getParameterName()).isEqualTo("zoneId");
                    assertThat(pce.getMatrixValue()).isEqualTo("zone-matrix");
                    assertThat(pce.getHeaderValue()).isEqualTo("zone-header");
                });
    }

    @Test
    void contextIdConflict_throwsException() {
        var matrix = Map.of("contextId", "ctx-matrix");
        var headers = Map.of("contextId", "ctx-header");

        assertThatThrownBy(() -> ParameterResolver.resolve(matrix, headers))
                .isInstanceOf(ParameterConflictException.class)
                .satisfies(ex -> {
                    var pce = (ParameterConflictException) ex;
                    assertThat(pce.getParameterName()).isEqualTo("contextId");
                    assertThat(pce.getMatrixValue()).isEqualTo("ctx-matrix");
                    assertThat(pce.getHeaderValue()).isEqualTo("ctx-header");
                });
    }

    @Test
    void deleteMessageIdConflict_throwsException() {
        var matrix = Map.of("messageId", "msg-matrix");
        var headers = Map.of("deleteMessageId", "msg-header");

        assertThatThrownBy(() -> ParameterResolver.resolve(matrix, headers))
                .isInstanceOf(ParameterConflictException.class)
                .satisfies(ex -> {
                    var pce = (ParameterConflictException) ex;
                    assertThat(pce.getParameterName()).isEqualTo("deleteMessageId");
                    assertThat(pce.getMatrixValue()).isEqualTo("msg-matrix");
                    assertThat(pce.getHeaderValue()).isEqualTo("msg-header");
                });
    }

    @Test
    void mixedPresence_resolvesIndependently() {
        // zoneId from matrix only, contextId from header only, deleteMessageId absent
        var matrix = Map.of("zoneId", "zone-from-matrix");
        var headers = Map.of("contextId", "ctx-from-header");
        var result = ParameterResolver.resolve(matrix, headers);

        assertThat(result.zoneId()).isEqualTo("zone-from-matrix");
        assertThat(result.contextId()).isEqualTo("ctx-from-header");
        assertThat(result.deleteMessageId()).isNull();
    }

    @Test
    void emptyStringValues_treatedAsAbsent() {
        var matrix = Map.of("zoneId", "");
        var headers = Map.of("zoneId", "zone-header");
        var result = ParameterResolver.resolve(matrix, headers);

        assertThat(result.zoneId()).isEqualTo("zone-header");
    }

    @Test
    void emptyStringInBothSources_returnsNull() {
        var matrix = Map.of("zoneId", "");
        var headers = Map.of("zoneId", "");
        var result = ParameterResolver.resolve(matrix, headers);

        assertThat(result.zoneId()).isNull();
    }
}
