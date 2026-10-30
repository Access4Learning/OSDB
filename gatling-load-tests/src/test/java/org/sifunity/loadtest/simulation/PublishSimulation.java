// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import org.sifunity.loadtest.auth.HmacAuthenticator;
import org.sifunity.loadtest.config.LoadTestConfig;
import org.sifunity.loadtest.data.AttendanceDataGenerator;
import org.sifunity.loadtest.state.SetupState;
import org.sifunity.loadtest.state.StateManager;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling simulation that publishes StudentDailyAttendance events from all
 * provider environments for {@link LoadTestConfig#DURATION_MINUTES} minutes.
 *
 * <p>Reads {@code target/setup-state.json} written by {@link SetupSimulation}.
 * Each virtual user is assigned a zone's provider credentials via a cycling
 * feeder and publishes events in a timed loop with fresh HMAC auth, payload,
 * and messageId per request.
 */
public class PublishSimulation extends Simulation {

    // ── Counters ───────────────────────────────────────────────────────
    private static final AtomicLong EVENTS_PUBLISHED = new AtomicLong();

    // ── Load state from setup phase ──────────────────────────────────
    SetupState state = StateManager.load();

    // ── HTTP protocol ────────────────────────────────────────────────
    HttpProtocolBuilder httpProtocol = http
        .baseUrl(LoadTestConfig.GATEWAY_URL)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json");

    // ── Feeder: cycles through all zone provider credentials ─────────
    // Circular feeder so THREAD_COUNT can exceed ZONE_COUNT — multiple users
    // can publish from the same zone's provider credentials concurrently.
    Iterator<Map<String, Object>> providerFeeder = Stream.generate(
            () -> state.zones().stream()
                .map(z -> Map.<String, Object>of(
                    "zoneId", z.zoneId(),
                    "providerSessionToken", z.providerSessionToken(),
                    "providerSharedSecret", z.providerSharedSecret()
                ))
                .collect(Collectors.toList()))
        .flatMap(List::stream)
        .iterator();

    // ── Scenario: publish events in a timed loop ─────────────────────
    ScenarioBuilder publishEvents = scenario("Publish Events")
        .feed(providerFeeder)
        .during(Duration.ofMinutes(LoadTestConfig.DURATION_MINUTES))
        .on(
            exec(session -> {
                String[] auth = HmacAuthenticator.generateAuthHeaders(
                    session.getString("providerSessionToken"),
                    session.getString("providerSharedSecret"));
                String payload = AttendanceDataGenerator.generateEventPayload(
                    LoadTestConfig.RECORDS_PER_EVENT);
                return session
                    .set("authHeader", auth[0])
                    .set("timestamp", auth[1])
                    .set("eventPayload", payload)
                    .set("messageId", UUID.randomUUID().toString());
            })
            .exec(http("Publish Event")
                .post("/" + state.domainId() + "/" + state.solutionId() + "/events")
                .header("Authorization", "#{authHeader}")
                .header("timestamp", "#{timestamp}")
                .header("zoneId", "#{zoneId}")
                .header("contextId", "DEFAULT")
                .header("messageType", "event")
                .header("messageId", "#{messageId}")
                .header("eventAction", "CREATE")
                .header("serviceType", "OBJECT")
                .header("serviceName", "StudentDailyAttendances")
                .body(StringBody("#{eventPayload}"))
                .check(status().in(202, 204)))
            .exec(session -> { EVENTS_PUBLISHED.incrementAndGet(); return session; })
        );

    // ── Wire up simulation ───────────────────────────────────────────
    {
        setUp(
            publishEvents.injectOpen(atOnceUsers(LoadTestConfig.THREAD_COUNT))
        ).protocols(httpProtocol);
    }

    @Override
    public void after() {
        long events = EVENTS_PUBLISHED.get();
        int rpe = LoadTestConfig.RECORDS_PER_EVENT;
        long totalRecords = events * rpe;
        System.out.printf("%n══ Publish Summary ══════════════════════════════════════════%n");
        System.out.printf("  Events published:  %,d%n", events);
        System.out.printf("  Records per event: %,d%n", rpe);
        System.out.printf("  Total records:     %,d%n", totalRecords);
        System.out.printf("═════════════════════════════════════════════════════════════%n%n");
    }
}
