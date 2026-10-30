// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.simulation;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import org.sifunity.loadtest.auth.HmacAuthenticator;
import org.sifunity.loadtest.config.LoadTestConfig;
import org.sifunity.loadtest.state.SetupState;
import org.sifunity.loadtest.state.StateManager;
import org.sifunity.loadtest.state.ZoneState;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Gatling simulation that creates all infrastructure for load testing:
 * domain, solution, zones, environments, ACLs, queues, and subscriptions.
 *
 * <p>Phase 1 (single user): creates domain and solution using admin credentials.
 * Phase 2 (THREAD_COUNT users): creates zones and per-zone infrastructure in parallel.
 * After completion: collects all ZoneState objects and writes SetupState via StateManager.
 */
public class SetupSimulation extends Simulation {

    // ── Shared state between phases ──────────────────────────────────────
    // Phase 1 stores these; Phase 2 reads them.
    private static volatile String sharedDomainId;
    private static volatile String sharedSolutionId;

    // Thread-safe accumulator for zone results from Phase 2 virtual users.
    private static final ConcurrentLinkedQueue<ZoneState> ZONE_STATES = new ConcurrentLinkedQueue<>();

    // ── HTTP protocol ────────────────────────────────────────────────────

    HttpProtocolBuilder httpProtocol = http
        .baseUrl(LoadTestConfig.GATEWAY_URL)
        .acceptHeader("application/json")
        .contentTypeHeader("application/json");

    // ── Feeder: distributes zone indices across virtual users ─────────

    /**
     * Build a queue-based feeder that hands out zone indices 0..ZONE_COUNT-1.
     * Each virtual user pulls the next available index and processes it.
     * When a user finishes one zone it pulls the next, until the queue is empty.
     */
    private static Iterator<Map<String, Object>> zoneFeeder() {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (int i = 0; i < LoadTestConfig.ZONE_COUNT; i++) {
            entries.add(Map.of("zoneIndex", i));
        }
        return entries.iterator();
    }

    // ── Phase 1: Create Domain & Solution (single admin user) ────────

    ScenarioBuilder createInfra = scenario("Create Domain & Solution")
        // Generate unique IDs and fresh admin auth
        .exec(session -> {
            String domainId = "loadtestdomain-" + System.currentTimeMillis();
            String solutionId = "loadtestsolution-" + System.currentTimeMillis();
            String[] auth = HmacAuthenticator.generateAuthHeaders(
                LoadTestConfig.ADMIN_SESSION_TOKEN, LoadTestConfig.ADMIN_SHARED_SECRET);
            return session
                .set("domainId", domainId)
                .set("solutionId", solutionId)
                .set("authHeader", auth[0])
                .set("timestamp", auth[1]);
        })
        // Create domain
        .exec(http("Create Domain")
            .post("/domains")
            .header("Authorization", "#{authHeader}")
            .header("timestamp", "#{timestamp}")
            .body(StringBody("{\"domainId\":\"#{domainId}\",\"description\":\"Gatling load test\"}"))
            .check(status().is(201)))
        // Refresh auth for solution creation
        .exec(session -> {
            String[] auth = HmacAuthenticator.generateAuthHeaders(
                LoadTestConfig.ADMIN_SESSION_TOKEN, LoadTestConfig.ADMIN_SHARED_SECRET);
            return session
                .set("authHeader", auth[0])
                .set("timestamp", auth[1]);
        })
        // Create solution
        .exec(http("Create Solution")
            .post("/#{domainId}/solutions")
            .header("Authorization", "#{authHeader}")
            .header("timestamp", "#{timestamp}")
            .body(StringBody("{\"solutionId\":\"#{solutionId}\",\"description\":\"Gatling load test\"}"))
            .check(status().is(201)))
        // Publish domainId/solutionId to static fields for Phase 2
        .exec(session -> {
            sharedDomainId = session.getString("domainId");
            sharedSolutionId = session.getString("solutionId");
            return session;
        });

    // ── Phase 2: Create Zones + per-zone infrastructure ──────────────

    ScenarioBuilder createZones = scenario("Create Zones")
        .feed(zoneFeeder())
        // Copy shared IDs into this virtual user's session
        .exec(session -> session
            .set("domainId", sharedDomainId)
            .set("solutionId", sharedSolutionId))
        // Each user creates one zone with all its infrastructure
        .exec(createZoneChain());

    /**
     * Builds the exec chain that creates a single zone and all its
     * infrastructure (environments, ACLs, queue, subscription).
     */
    private static ChainBuilder createZoneChain() {
        return exec(session -> {
                int idx = session.getInt("zoneIndex");
                String zoneId = "loadzone-" + idx;
                return session.set("zoneId", zoneId);
            })
            // ── Create Zone ──
            .exec(refreshAdminAuth())
            .exec(http("Create Zone")
                .post("/#{domainId}/#{solutionId}/zones")
                .header("Authorization", "#{authHeader}")
                .header("timestamp", "#{timestamp}")
                .body(StringBody("{\"zoneId\":\"#{zoneId}\",\"description\":\"Load test zone #{zoneIndex}\"}"))
                .check(status().is(201)))
            // ── Get DEFAULT context UUID ──
            .exec(refreshAdminAuth())
            .exec(http("Get Contexts")
                .get("/#{domainId}/#{solutionId}/zones/#{zoneId}/contexts")
                .header("Authorization", "#{authHeader}")
                .header("timestamp", "#{timestamp}")
                .check(status().is(200))
                .check(jsonPath("$.contexts.context[?(@.contextId=='DEFAULT')].id").saveAs("contextUuid")))
            // ── Create Provider Environment (unique name per run) ──
            .exec(session -> session.set("runTs", String.valueOf(System.currentTimeMillis())))
            .exec(refreshAdminAuth())
            .exec(http("Create Provider Environment")
                .post("/#{domainId}/#{solutionId}/environments/environment")
                .header("Authorization", "#{authHeader}")
                .header("timestamp", "#{timestamp}")
                .body(StringBody(
                    "{\"applicationName\":\"loadtest-provider-#{zoneIndex}-#{runTs}\",\"type\":\"BROKERED\"}"))
                .check(status().is(201))
                .check(jsonPath("$.environment.id").saveAs("providerEnvId"))
                .check(jsonPath("$.environment.sessionToken").saveAs("providerSessionToken"))
                .check(jsonPath("$.environment.sharedSecret").saveAs("providerSharedSecret")))
            // ── Create Consumer Environment (unique name per run) ──
            .exec(refreshAdminAuth())
            .exec(http("Create Consumer Environment")
                .post("/#{domainId}/#{solutionId}/environments/environment")
                .header("Authorization", "#{authHeader}")
                .header("timestamp", "#{timestamp}")
                .body(StringBody(
                    "{\"applicationName\":\"loadtest-consumer-#{zoneIndex}-#{runTs}\",\"type\":\"BROKERED\"}"))
                .check(status().is(201))
                .check(jsonPath("$.environment.id").saveAs("consumerEnvId"))
                .check(jsonPath("$.environment.sessionToken").saveAs("consumerSessionToken"))
                .check(jsonPath("$.environment.sharedSecret").saveAs("consumerSharedSecret")))
            // ── Create PROVIDE ACL ──
            .exec(refreshAdminAuth())
            .exec(http("Create PROVIDE ACL")
                .post("/#{domainId}/#{solutionId}/acl-entries")
                .header("Authorization", "#{authHeader}")
                .header("timestamp", "#{timestamp}")
                .body(StringBody(
                    "{\"environmentId\":\"#{providerEnvId}\","
                    + "\"serviceName\":\"StudentDailyAttendances\","
                    + "\"contextUuid\":\"#{contextUuid}\","
                    + "\"accessType\":\"PROVIDE\","
                    + "\"accessLevel\":\"APPROVED\"}"))
                .check(status().is(201))
                .check(jsonPath("$.id").saveAs("providerAclId")))
            // ── Create SUBSCRIBE ACL ──
            .exec(refreshAdminAuth())
            .exec(http("Create SUBSCRIBE ACL")
                .post("/#{domainId}/#{solutionId}/acl-entries")
                .header("Authorization", "#{authHeader}")
                .header("timestamp", "#{timestamp}")
                .body(StringBody(
                    "{\"environmentId\":\"#{consumerEnvId}\","
                    + "\"serviceName\":\"StudentDailyAttendances\","
                    + "\"contextUuid\":\"#{contextUuid}\","
                    + "\"accessType\":\"SUBSCRIBE\","
                    + "\"accessLevel\":\"APPROVED\"}"))
                .check(status().is(201))
                .check(jsonPath("$.id").saveAs("consumerAclId")))
            // ── Create Queue (authenticated as consumer, not admin) ──
            .exec(session -> {
                String[] auth = HmacAuthenticator.generateAuthHeaders(
                    session.getString("consumerSessionToken"),
                    session.getString("consumerSharedSecret"));
                return session
                    .set("authHeader", auth[0])
                    .set("timestamp", auth[1]);
            })
            .exec(http("Create Queue")
                .post("/#{domainId}/#{solutionId}/queues")
                .header("Authorization", "#{authHeader}")
                .header("timestamp", "#{timestamp}")
                .body(StringBody(
                    "{\"name\":\"DEFAULTQUEUE\","
                    + "\"ownerId\":\"#{consumerEnvId}\","
                    + "\"maxRetries\":3,"
                    + "\"retryDelayMs\":5000}"))
                .check(status().is(201))
                .check(jsonPath("$.id").saveAs("consumerQueueId")))
            // ── Create Subscription (authenticated as consumer) ──
            .exec(session -> {
                String[] auth = HmacAuthenticator.generateAuthHeaders(
                    session.getString("consumerSessionToken"),
                    session.getString("consumerSharedSecret"));
                return session
                    .set("authHeader", auth[0])
                    .set("timestamp", auth[1]);
            })
            .exec(http("Create Subscription")
                .post("/#{domainId}/#{solutionId}/subscriptions")
                .header("Authorization", "#{authHeader}")
                .header("timestamp", "#{timestamp}")
                .body(StringBody(
                    "{\"queueId\":\"#{consumerQueueId}\","
                    + "\"aclEntryId\":\"#{consumerAclId}\","
                    + "\"messageType\":\"EVENT\"}"))
                .check(status().is(201))
                .check(jsonPath("$.id").saveAs("consumerSubscriptionId")))
            // ── Collect ZoneState into thread-safe queue ──
            .exec(session -> {
                ZoneState zs = new ZoneState(
                    session.getString("zoneId"),
                    session.getString("contextUuid"),
                    session.getString("providerEnvId"),
                    session.getString("providerSessionToken"),
                    session.getString("providerSharedSecret"),
                    session.getString("consumerEnvId"),
                    session.getString("consumerSessionToken"),
                    session.getString("consumerSharedSecret"),
                    session.getString("consumerQueueId"),
                    session.getString("consumerAclId"),
                    session.getString("consumerSubscriptionId"));
                ZONE_STATES.add(zs);
                return session;
            });
    }

    /**
     * Refresh admin HMAC auth headers in the session before each HTTP call.
     */
    private static ChainBuilder refreshAdminAuth() {
        return exec(session -> {
            String[] auth = HmacAuthenticator.generateAuthHeaders(
                LoadTestConfig.ADMIN_SESSION_TOKEN, LoadTestConfig.ADMIN_SHARED_SECRET);
            return session
                .set("authHeader", auth[0])
                .set("timestamp", auth[1]);
        });
    }

    // ── Phase 3: Save state after all zones complete ─────────────────

    ScenarioBuilder saveState = scenario("Save Setup State")
        .exec(session -> {
            List<ZoneState> zones = new ArrayList<>(ZONE_STATES);
            zones.sort(Comparator.comparing(ZoneState::zoneId));
            SetupState state = new SetupState(sharedDomainId, sharedSolutionId, zones);
            StateManager.save(state);
            System.out.println("SetupSimulation: saved " + zones.size()
                + " zones to target/setup-state.json");
            return session;
        });

    // ── Wire up phases ───────────────────────────────────────────────

    {
        // Inject ZONE_COUNT users (one per zone), ramped over a few seconds
        // to avoid overwhelming the gateway. THREAD_COUNT controls concurrency.
        int rampSeconds = Math.max(1, LoadTestConfig.ZONE_COUNT / LoadTestConfig.THREAD_COUNT);
        setUp(
            createInfra.injectOpen(atOnceUsers(1))
                .andThen(
                    createZones.injectOpen(
                        rampUsers(LoadTestConfig.ZONE_COUNT).during(rampSeconds))
                        .andThen(
                            saveState.injectOpen(atOnceUsers(1))
                        )
                )
        ).protocols(httpProtocol);
    }
}
