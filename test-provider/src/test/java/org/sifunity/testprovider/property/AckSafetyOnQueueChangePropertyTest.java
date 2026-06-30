// SPDX-License-Identifier: MIT
package org.sifunity.testprovider.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.sifunity.testprovider.config.ConfigSnapshot;
import org.sifunity.testprovider.config.ProviderConfig;
import org.sifunity.testprovider.service.DelayedPollRunner;
import org.sifunity.testprovider.service.TestDataGenerator;

import java.lang.reflect.Field;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 8: ACK safety on queue change.
 *
 * <p><strong>Validates: Requirements 4.3</strong></p>
 *
 * <p>When {@code queueId} changes between the before/after snapshots passed to
 * {@link DelayedPollRunner#onConfigChanged}, any pending ACK (the stored {@code pendingAckMessageId})
 * must be reset to {@code null} so a stale ACK from the old queue is never replayed against a new queue.
 * When {@code queueId} is unchanged, the pending ACK must be preserved.</p>
 */
class AckSafetyOnQueueChangePropertyTest {

    /**
     * queueId values are drawn from a small set (including {@code null}) so that, across tries, the
     * before/after pair is sometimes equal and sometimes different — exercising both branches.
     */
    @Provide
    Arbitrary<String> queueIds() {
        return Arbitraries.of(null, "queue-a", "queue-b", "queue-c", "");
    }

    @Property(tries = 20)
    void pendingAckResetIffQueueChanges(
            @ForAll("queueIds") String beforeQueueId,
            @ForAll("queueIds") String afterQueueId) throws Exception {

        DelayedPollRunner runner = new DelayedPollRunner(
                new ProviderConfig(), new TestDataGenerator(), new ObjectMapper());

        // Seed a non-null pending ACK so we can observe whether it is reset.
        String seededAck = "msg-12345";
        setPendingAck(runner, seededAck);

        ConfigSnapshot before = snapshotWithQueue(beforeQueueId);
        ConfigSnapshot after = snapshotWithQueue(afterQueueId);

        runner.onConfigChanged(before, after);

        String pendingAck = getPendingAck(runner);
        if (!Objects.equals(beforeQueueId, afterQueueId)) {
            assertThat(pendingAck)
                    .as("queueId changed (%s -> %s): pending ACK must be reset to null", beforeQueueId, afterQueueId)
                    .isNull();
        } else {
            assertThat(pendingAck)
                    .as("queueId unchanged (%s): pending ACK must be preserved", beforeQueueId)
                    .isEqualTo(seededAck);
        }
    }

    private static ConfigSnapshot snapshotWithQueue(String queueId) {
        return new ConfigSnapshot(
                "http://localhost:8080",
                "session-token",
                "shared-secret",
                "domain-1",
                "solution-1",
                queueId,
                9091);
    }

    private static void setPendingAck(DelayedPollRunner runner, String value) throws Exception {
        Field field = DelayedPollRunner.class.getDeclaredField("pendingAckMessageId");
        field.setAccessible(true);
        field.set(runner, value);
    }

    private static String getPendingAck(DelayedPollRunner runner) throws Exception {
        Field field = DelayedPollRunner.class.getDeclaredField("pendingAckMessageId");
        field.setAccessible(true);
        return (String) field.get(runner);
    }
}
