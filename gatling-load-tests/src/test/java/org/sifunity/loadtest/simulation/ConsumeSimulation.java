// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import org.sifunity.loadtest.auth.HmacAuthenticator;
import org.sifunity.loadtest.config.LoadTestConfig;
import org.sifunity.loadtest.state.SetupState;
import org.sifunity.loadtest.state.StateManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling simulation that drains all consumer queues by polling with
 * {@link LoadTestConfig#CONNECTIONS_PER_CONSUMER} concurrent connections
 * per consumer environment.
 *
 * <p>Reads {@code target/setup-state.json} written by {@link SetupSimulation}.
 * Each virtual user is assigned a (consumer, connectionId) pair via a feeder
 * and polls in a loop until the queue returns HTTP 204 (empty).
 *
 * <p>Uses the SIF "Get Next and Pop" pattern:
 * <ol>
 *   <li>First GET returns oldest undelivered message + {@code messageid} header</li>
 *   <li>Next GET includes {@code ;deleteMessageId={id}} matrix param to ACK + fetch next</li>
 *   <li>HTTP 204 means queue is empty → stop polling</li>
 * </ol>
 */
public class ConsumeSimulation extends Simulation {

    // ── Counters ───────────────────────────────────────────────────────
    private static final AtomicLong MESSAGES_CONSUMED = new AtomicLong();

    // ── Load state from setup phase ──────────────────────────────────
    SetupState state = StateManager.load();

    // ── HTTP protocol ────────────────────────────────────────────────
    HttpProtocolBuilder httpProtocol = http
        .baseUrl(LoadTestConfig.GATEWAY_URL)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json");

    // ── Feeder: each consumer × each connectionId ────────────────────
    Iterator<Map<String, Object>> consumerFeeder = state.zones().stream()
        .flatMap(z -> IntStream.rangeClosed(1, LoadTestConfig.CONNECTIONS_PER_CONSUMER)
            .mapToObj(connId -> Map.<String, Object>of(
                "queueId", z.consumerQueueId(),
                "consumerSessionToken", z.consumerSessionToken(),
                "consumerSharedSecret", z.consumerSharedSecret(),
                "connectionId", String.valueOf(connId)
            )))
        .iterator();

    // ── Scenario: poll messages until queue is drained ────────────────
    ScenarioBuilder consumeMessages = scenario("Consume Messages")
        .feed(consumerFeeder)
        .exec(session -> session
            .set("deleteMessageId", "")
            .set("queueDrained", false))
        .asLongAs(session -> !session.getBoolean("queueDrained"))
        .on(
            // Compute fresh HMAC auth headers for this request
            exec(session -> {
                String[] auth = HmacAuthenticator.generateAuthHeaders(
                    session.getString("consumerSessionToken"),
                    session.getString("consumerSharedSecret"));
                return session
                    .set("authHeader", auth[0])
                    .set("timestamp", auth[1]);
            })
            // Build the poll URL with SIF matrix param syntax for deleteMessageId
            // (gateway MatrixParamRewriteFilter converts ;key=value to ?key=value)
            .exec(session -> {
                String baseUrl = "/" + state.domainId() + "/" + state.solutionId()
                    + "/queues/" + session.getString("queueId")
                    + "/messages";
                String deleteId = session.getString("deleteMessageId");
                if (deleteId != null && !deleteId.isEmpty()) {
                    baseUrl += ";deleteMessageId=" + deleteId;
                }
                return session.set("pollUrl", baseUrl);
            })
            // GET message from queue
            .exec(http("Get Message")
                .get("#{pollUrl}")
                .header("Authorization", "#{authHeader}")
                .header("timestamp", "#{timestamp}")
                .header("connectionId", "#{connectionId}")
                .check(status().in(200, 204))
                .check(status().saveAs("lastStatus"))
                .check(header("messageid").optional().saveAs("lastMessageId")))
            // Process response: extract messageId on 200, mark drained on 204
            .exec(session -> {
                int lastStatus = session.getInt("lastStatus");
                if (lastStatus == 200) {
                    MESSAGES_CONSUMED.incrementAndGet();
                    return session
                        .set("deleteMessageId", session.getString("lastMessageId"))
                        .set("queueDrained", false);
                } else {
                    // 204 — queue empty
                    return session.set("queueDrained", true);
                }
            })
        );

    // ── Wire up simulation ───────────────────────────────────────────
    // Inject one virtual user per (queue, connectionId) pair so every queue
    // gets CONNECTIONS_PER_CONSUMER parallel pollers.
    {
        int totalConsumers = state.zones().size() * LoadTestConfig.CONNECTIONS_PER_CONSUMER;
        setUp(
            consumeMessages.injectOpen(atOnceUsers(totalConsumers))
        ).protocols(httpProtocol);
    }

    @Override
    public void after() {
        long messages = MESSAGES_CONSUMED.get();
        int rpe = LoadTestConfig.RECORDS_PER_EVENT;
        long totalRecords = messages * rpe;
        System.out.printf("%n══ Consume Summary ══════════════════════════════════════════%n");
        System.out.printf("  Messages consumed: %,d%n", messages);
        System.out.printf("  Records per event: %,d%n", rpe);
        System.out.printf("  Total records:     %,d%n", totalRecords);
        System.out.printf("═════════════════════════════════════════════════════════════%n%n");
    }
}
