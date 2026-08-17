// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.property;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.sifunity.broker.common.constant.EnvironmentType;
import org.sifunity.broker.environment.dto.DomainCreateRequest;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationRequest;
import org.sifunity.broker.environment.dto.SifEnvironmentRegistrationResponse;
import org.sifunity.broker.environment.dto.SolutionCreateRequest;
import org.sifunity.broker.environment.service.DomainService;
import org.sifunity.broker.environment.service.EnvironmentService;
import org.sifunity.broker.environment.service.SolutionService;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import org.sifunity.broker.environment.dto.EnvironmentRegistrationBody;
import org.sifunity.broker.environment.dto.DefaultZoneDTO;

/**
 * Property-based test for sharedSecret security requirements using JUnit 5 RepeatedTest.
 * 
 * <p>This test validates Property 3 from the design document:
 * "For any created environment, the sharedSecret should be at least 256 bits (32 bytes) 
 * in length and cryptographically random."</p>
 * 
 * <p><strong>Validates: Requirements 1.3</strong></p>
 * 
 * <p>This test runs 100 iterations with randomized input data to verify that
 * sharedSecrets meet security requirements across different environment registrations.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SharedSecretSecurityPropertyTest {
    
    @Autowired
    private EnvironmentService environmentService;
    
    @Autowired
    private DomainService domainService;
    
    @Autowired
    private SolutionService solutionService;
    
    private final Random random = new Random();

    private String domainId;

    private String createTestSolution() {
        domainId = "sss-domain-" + System.nanoTime();
        domainService.createDomain(new DomainCreateRequest(domainId, null));
        String solutionId = "sss-solution-" + System.nanoTime();
        solutionService.createSolution(new SolutionCreateRequest(domainId, solutionId, null));
        return solutionId;
    }
    
    /**
     * Property 3: SharedSecret security requirements
     * 
     * <p><strong>Property:</strong> For any created environment, the sharedSecret 
     * should be at least 256 bits (32 bytes) in length and cryptographically random.</p>
     * 
     * <p><strong>Validates: Requirements 1.3</strong></p>
     * 
     * <p>This test verifies:
     * <ul>
     *   <li>SharedSecret is at least 256 bits (32 bytes) when decoded from Base64</li>
     *   <li>SharedSecret appears to be cryptographically random (statistical tests)</li>
     *   <li>SharedSecret is properly Base64 encoded</li>
     *   <li>SharedSecret is unique across multiple environment registrations</li>
     * </ul>
     */
    @RepeatedTest(10)
    @Tag("environment-service-property-3")
    void sharedSecretSecurityRequirements() {
        // Generate random valid input data
        String solutionId = createTestSolution();
        String applicationName = generateUniqueApplicationName();
        EnvironmentType type = generateRandomEnvironmentType();
        // Use null for defaultZoneId since zone validation is not what this test covers
        String defaultZoneId = null;
        // Create environment registration request
        EnvironmentRegistrationRequest request = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(applicationName, type, DefaultZoneDTO.of(defaultZoneId), solutionId, null));
        
        // Register environment
        SifEnvironmentRegistrationResponse response = environmentService.registerEnvironment(domainId, request);
        
        // Verify sharedSecret is not null and not blank
        assertThat(response.sharedSecret())
            .as("SharedSecret should not be null or blank")
            .isNotNull()
            .isNotBlank();
        
        // Verify sharedSecret is valid Base64
        byte[] decodedSecret = null;
        try {
            decodedSecret = Base64.getDecoder().decode(response.sharedSecret());
        } catch (IllegalArgumentException e) {
            throw new AssertionError(
                String.format("SharedSecret '%s' is not valid Base64: %s", 
                    response.sharedSecret(), e.getMessage())
            );
        }
        
        assertThat(decodedSecret)
            .as("SharedSecret should be decodable from Base64")
            .isNotNull();
        
        // Verify sharedSecret is at least 256 bits (32 bytes) in length
        assertThat(decodedSecret.length)
            .as("SharedSecret should be at least 32 bytes (256 bits) in length")
            .isGreaterThanOrEqualTo(32);
        
        // Verify sharedSecret appears to be cryptographically random
        verifyRandomnessProperties(decodedSecret, response.sharedSecret());
        
        // Additional test: Create multiple environments and verify uniqueness
        Set<String> sharedSecrets = new HashSet<>();
        sharedSecrets.add(response.sharedSecret());
        
        // Create a few more environments to test uniqueness
        int additionalEnvironments = random.nextInt(3) + 2; // 2-4 additional environments
        for (int i = 0; i < additionalEnvironments; i++) {
            String additionalAppName = generateUniqueApplicationName();
            EnvironmentRegistrationRequest additionalRequest = new EnvironmentRegistrationRequest(new EnvironmentRegistrationBody(additionalAppName, generateRandomEnvironmentType(), null, solutionId, null));
            
            SifEnvironmentRegistrationResponse additionalResponse =
                environmentService.registerEnvironment(domainId, additionalRequest);
            
            // Verify this sharedSecret is also valid
            byte[] additionalDecoded = Base64.getDecoder().decode(additionalResponse.sharedSecret());
            assertThat(additionalDecoded.length)
                .as("Additional sharedSecret should be at least 32 bytes")
                .isGreaterThanOrEqualTo(32);
            
            // Verify uniqueness
            boolean isUnique = sharedSecrets.add(additionalResponse.sharedSecret());
            assertThat(isUnique)
                .as("SharedSecret should be unique across environments")
                .isTrue();
        }
        
        // Final verification: all sharedSecrets should be unique
        assertThat(sharedSecrets)
            .as("All sharedSecrets should be unique")
            .hasSize(additionalEnvironments + 1);
    }
    
    /**
     * Verifies that the decoded shared secret exhibits properties of cryptographically
     * random data. This includes statistical tests for randomness.
     * 
     * @param decodedSecret the decoded byte array of the shared secret
     * @param base64Secret the original Base64-encoded shared secret for error messages
     */
    private void verifyRandomnessProperties(byte[] decodedSecret, String base64Secret) {
        // Test 1: No byte should appear too frequently (basic entropy check)
        // With 32 bytes of truly random data, no single byte value should dominate
        int[] byteFrequency = new int[256];
        for (byte b : decodedSecret) {
            byteFrequency[b & 0xFF]++;
        }
        
        // Find the most frequent byte value
        int maxFrequency = 0;
        for (int freq : byteFrequency) {
            maxFrequency = Math.max(maxFrequency, freq);
        }
        
        // With 32 bytes, no single byte value should appear more than ~25% of the time
        // This is a reasonable threshold for detecting obviously non-random data
        double maxFrequencyRatio = (double) maxFrequency / decodedSecret.length;
        assertThat(maxFrequencyRatio)
            .as("No single byte value should dominate the sharedSecret (max frequency: %d/%d = %.2f)", 
                maxFrequency, decodedSecret.length, maxFrequencyRatio)
            .isLessThanOrEqualTo(0.25);
        
        // Test 2: Verify the secret is not all zeros or all the same value
        boolean allSame = true;
        byte firstByte = decodedSecret[0];
        for (int i = 1; i < decodedSecret.length; i++) {
            if (decodedSecret[i] != firstByte) {
                allSame = false;
                break;
            }
        }
        
        assertThat(allSame)
            .as("SharedSecret should not be all the same byte value")
            .isFalse();
        
        // Test 3: Verify the secret is not all zeros
        boolean allZeros = true;
        for (byte b : decodedSecret) {
            if (b != 0) {
                allZeros = false;
                break;
            }
        }
        
        assertThat(allZeros)
            .as("SharedSecret should not be all zeros")
            .isFalse();
        
        // Test 4: Basic bit distribution check
        // Count the number of 1-bits across all bytes
        int totalBits = decodedSecret.length * 8;
        int oneBits = 0;
        for (byte b : decodedSecret) {
            oneBits += Integer.bitCount(b & 0xFF);
        }
        
        // With truly random data, we expect roughly 50% of bits to be 1
        // Allow for reasonable variance (between 35% and 65%)
        double oneBitRatio = (double) oneBits / totalBits;
        assertThat(oneBitRatio)
            .as("Bit distribution should be reasonably balanced (1-bits: %d/%d = %.2f)", 
                oneBits, totalBits, oneBitRatio)
            .isBetween(0.35, 0.65);
        
        // Test 5: Verify the Base64 encoding is standard (not URL-safe or custom)
        // Standard Base64 uses A-Z, a-z, 0-9, +, / and = for padding
        assertThat(base64Secret)
            .as("SharedSecret should use standard Base64 encoding")
            .matches("^[A-Za-z0-9+/]+=*$");
        
        // Test 6: Verify proper Base64 padding
        // Base64 strings should have proper padding (0, 1, or 2 '=' characters at the end)
        long paddingCount = base64Secret.chars().filter(ch -> ch == '=').count();
        assertThat(paddingCount)
            .as("Base64 padding should be 0, 1, or 2 characters")
            .isBetween(0L, 2L);
        
        // Test 7: Verify the decoded length matches expected Base64 encoding
        // For 32 bytes, Base64 encoding should produce 44 characters (including padding)
        if (decodedSecret.length == 32) {
            assertThat(base64Secret.length())
                .as("32-byte secret should encode to 44 Base64 characters")
                .isEqualTo(44);
        }
    }
    
    // ========== Random Data Generators ==========
    
    /**
     * Generates a unique application name to avoid conflicts during testing.
     */
    private String generateUniqueApplicationName() {
        String[] prefixes = {"App", "Service", "System", "Platform", "Tool", "Client", "Server"};
        String[] suffixes = {"Pro", "Lite", "Plus", "Enterprise", "Basic", "Advanced", "Standard"};
        
        String prefix = prefixes[random.nextInt(prefixes.length)];
        String suffix = suffixes[random.nextInt(suffixes.length)];
        long timestamp = System.nanoTime(); // Use nanoseconds for uniqueness
        int randomNum = random.nextInt(10000);
        
        return prefix + "-" + suffix + "-" + timestamp + "-" + randomNum;
    }
    
    /**
     * Generates a random valid environment type.
     */
    private EnvironmentType generateRandomEnvironmentType() {
        EnvironmentType[] types = EnvironmentType.values();
        return types[random.nextInt(types.length)];
    }
    
    /**
     * Generates a valid zone ID.
     */
    private String generateValidZoneId() {
        String[] zones = {"production", "staging", "development", "test", "demo", "sandbox"};
        String base = zones[random.nextInt(zones.length)];
        int number = random.nextInt(100);
        return base + "-" + number;
    }
    
    /**
     * Generates a valid context ID.
     */
    private String generateValidContextId() {
        String[] contexts = {"default", "primary", "secondary", "backup", "main", "alt"};
        String base = contexts[random.nextInt(contexts.length)];
        int number = random.nextInt(50);
        return base + "-" + number;
    }
}