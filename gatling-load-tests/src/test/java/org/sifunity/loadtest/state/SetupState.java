// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.state;

import java.util.List;

/**
 * Top-level state written after {@code SetupSimulation} completes and read by
 * {@code PublishSimulation} and {@code ConsumeSimulation}. Contains the domain,
 * solution, and all per-zone credentials/IDs needed for the load test phases.
 */
public record SetupState(
    String domainId,
    String solutionId,
    List<ZoneState> zones
) {}
