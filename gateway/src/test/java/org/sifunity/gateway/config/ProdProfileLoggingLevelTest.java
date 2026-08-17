// SPDX-License-Identifier: MIT
package org.sifunity.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the gateway prod profile correctly overrides the base DEBUG level
 * for org.sifunity.gateway to INFO (or higher), ensuring verbose auth/routing output
 * does not ship to production logs.
 *
 * <p>This is a lightweight YAML-parsing test that does NOT boot the full Spring context.
 * It reads application.yml and application-prod.yml directly and asserts the logging
 * level entries are correct.
 *
 * <p><b>Validates: Requirements 2.3, 4.1</b>
 */
@DisplayName("Production Profile Logging Level Verification")
class ProdProfileLoggingLevelTest {

    private static final Yaml YAML = new Yaml();

    @Test
    @DisplayName("base application.yml sets org.sifunity.gateway to DEBUG")
    void baseConfig_setsGatewayPackageToDebug() {
        Map<String, Object> config = loadYaml("application.yml");
        String level = getLoggingLevel(config, "org.sifunity.gateway");

        assertThat(level)
                .as("Base config should set org.sifunity.gateway to DEBUG for local dev")
                .isEqualToIgnoringCase("DEBUG");
    }

    @Test
    @DisplayName("application-prod.yml overrides org.sifunity.gateway to INFO")
    void prodProfile_overridesGatewayPackageToInfo() {
        Map<String, Object> config = loadYaml("application-prod.yml");
        String level = getLoggingLevel(config, "org.sifunity.gateway");

        assertThat(level)
                .as("Prod profile must override org.sifunity.gateway to INFO (not DEBUG)")
                .isEqualToIgnoringCase("INFO");
    }

    @Test
    @DisplayName("application-prod.yml sets root logging level to WARN")
    void prodProfile_setsRootToWarn() {
        Map<String, Object> config = loadYaml("application-prod.yml");
        String level = getLoggingLevel(config, "root");

        assertThat(level)
                .as("Prod profile should set root log level to WARN for reduced noise")
                .isEqualToIgnoringCase("WARN");
    }

    @Test
    @DisplayName("application-prod.yml does NOT reference the incorrect org.sifunity.broker.gateway package")
    void prodProfile_doesNotReferenceIncorrectPackage() {
        Map<String, Object> config = loadYaml("application-prod.yml");
        String level = getLoggingLevel(config, "org.sifunity.broker.gateway");

        assertThat(level)
                .as("Prod profile must not contain the dead org.sifunity.broker.gateway override")
                .isNull();
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(String filename) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(filename);
        assertThat(is)
                .as("YAML resource must be loadable: " + filename)
                .isNotNull();
        return YAML.load(is);
    }

    /**
     * Navigate the YAML tree: logging.level.{packageName}
     */
    @SuppressWarnings("unchecked")
    private String getLoggingLevel(Map<String, Object> config, String packageName) {
        Object logging = config.get("logging");
        if (!(logging instanceof Map)) return null;

        Object level = ((Map<String, Object>) logging).get("level");
        if (!(level instanceof Map)) return null;

        Object value = ((Map<String, Object>) level).get(packageName);
        return value != null ? value.toString() : null;
    }
}
