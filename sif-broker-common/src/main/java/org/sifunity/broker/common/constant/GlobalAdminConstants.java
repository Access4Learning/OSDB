// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.constant;

/**
 * Constants and utility methods for identifying global admin environments.
 *
 * <p>Admin environments are identified by their membership in a well-known
 * global domain ({@link #GLOBAL_DOMAIN}) and solution ({@link #GLOBAL_SOLUTION}),
 * rather than by a special environment type.</p>
 */
public final class GlobalAdminConstants {

    /** The well-known domain ID for the global admin domain. */
    public static final String GLOBAL_DOMAIN = "GLOBAL_DOMAIN";

    /** The well-known solution ID for the global admin solution. */
    public static final String GLOBAL_SOLUTION = "GLOBAL_SOLUTION";

    /**
     * Checks if the given domainId and solutionId identify a global admin environment.
     * Both must match because solution_id uniqueness is scoped to (domain_id, solution_id).
     *
     * @param domainId   the domain ID to check (may be null)
     * @param solutionId the solution ID to check (may be null)
     * @return {@code true} if both match the global admin constants, {@code false} otherwise
     */
    public static boolean isGlobalAdmin(String domainId, String solutionId) {
        return GLOBAL_DOMAIN.equals(domainId) && GLOBAL_SOLUTION.equals(solutionId);
    }

    private GlobalAdminConstants() {
        // Utility class — prevent instantiation
    }
}
