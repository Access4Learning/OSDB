// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.secrets.migration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MigrationController}.
 * 
 * <p>These tests validate the REST endpoints for migration job monitoring and
 * manual triggering. Tests verify that the status endpoint returns correct
 * MigrationStatus data and that the trigger endpoint properly starts migration
 * jobs while preventing concurrent executions.</p>
 * 
 * <p><strong>Validates: Requirements 11.1</strong></p>
 */
@ExtendWith(MockitoExtension.class)
class MigrationControllerTest {

    @Mock
    private MigrationJob migrationJob;

    @InjectMocks
    private MigrationController migrationController;

    private MigrationStatus testStatus;

    @BeforeEach
    void setUp() {
        testStatus = createTestStatus(false);
    }

    // ========== Status Endpoint Tests ==========

    /**
     * Test that GET /api/migration/status returns correct MigrationStatus.
     * 
     * <p>The status endpoint should return the current migration status
     * from the MigrationJob.</p>
     */
    @Test
    void shouldReturnMigrationStatus() {
        // Given: MigrationJob has a status
        when(migrationJob.getStatus()).thenReturn(testStatus);

        // When: Status endpoint is called
        ResponseEntity<MigrationStatus> response = migrationController.getStatus();

        // Then: Response should be OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // And: Response body should contain the status
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isEqualTo(testStatus);
        
        // And: MigrationJob.getStatus() should be called once
        verify(migrationJob, times(1)).getStatus();
    }

    /**
     * Test that status endpoint returns correct data fields.
     * 
     * <p>Verifies that all fields in MigrationStatus are properly returned.</p>
     */
    @Test
    void shouldReturnStatusWithAllFields() {
        // Given: MigrationJob has a complete status
        MigrationStatus status = new MigrationStatus();
        status.setLastRun(Instant.parse("2024-01-15T02:00:00Z"));
        status.setTotalEnvironments(150);
        status.setMigrated(148);
        status.setFailed(2);
        status.setCurrentKeyVersion("20240115");
        status.setInProgress(false);
        
        when(migrationJob.getStatus()).thenReturn(status);

        // When: Status endpoint is called
        ResponseEntity<MigrationStatus> response = migrationController.getStatus();

        // Then: All fields should be present
        MigrationStatus body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getLastRun()).isEqualTo(Instant.parse("2024-01-15T02:00:00Z"));
        assertThat(body.getTotalEnvironments()).isEqualTo(150);
        assertThat(body.getMigrated()).isEqualTo(148);
        assertThat(body.getFailed()).isEqualTo(2);
        assertThat(body.getCurrentKeyVersion()).isEqualTo("20240115");
        assertThat(body.isInProgress()).isFalse();
    }

    /**
     * Test that status endpoint returns status when migration is in progress.
     * 
     * <p>The status endpoint should work correctly even when a migration
     * is currently running.</p>
     */
    @Test
    void shouldReturnStatusWhenMigrationInProgress() {
        // Given: Migration is in progress
        MigrationStatus inProgressStatus = createTestStatus(true);
        when(migrationJob.getStatus()).thenReturn(inProgressStatus);

        // When: Status endpoint is called
        ResponseEntity<MigrationStatus> response = migrationController.getStatus();

        // Then: Response should indicate migration in progress
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isInProgress()).isTrue();
    }

    /**
     * Test that status endpoint returns status with zero counts.
     * 
     * <p>Edge case: status with no environments or no migrations yet.</p>
     */
    @Test
    void shouldReturnStatusWithZeroCounts() {
        // Given: Status with zero counts
        MigrationStatus emptyStatus = new MigrationStatus();
        emptyStatus.setLastRun(null);
        emptyStatus.setTotalEnvironments(0);
        emptyStatus.setMigrated(0);
        emptyStatus.setFailed(0);
        emptyStatus.setCurrentKeyVersion("20240115");
        emptyStatus.setInProgress(false);
        
        when(migrationJob.getStatus()).thenReturn(emptyStatus);

        // When: Status endpoint is called
        ResponseEntity<MigrationStatus> response = migrationController.getStatus();

        // Then: Response should contain zero counts
        MigrationStatus body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getTotalEnvironments()).isZero();
        assertThat(body.getMigrated()).isZero();
        assertThat(body.getFailed()).isZero();
        assertThat(body.getLastRun()).isNull();
    }

    // ========== Trigger Endpoint Tests ==========

    /**
     * Test that POST /api/migration/trigger starts migration.
     * 
     * <p>When no migration is in progress, the trigger endpoint should
     * start a new migration and return the updated status.</p>
     */
    @Test
    void shouldTriggerMigrationWhenNotInProgress() {
        // Given: No migration in progress
        MigrationStatus beforeStatus = createTestStatus(false);
        MigrationStatus afterStatus = createTestStatus(false);
        afterStatus.setMigrated(150);
        afterStatus.setFailed(0);
        
        when(migrationJob.getStatus())
            .thenReturn(beforeStatus)  // First call: check if in progress
            .thenReturn(afterStatus);  // Second call: get updated status

        // When: Trigger endpoint is called
        ResponseEntity<MigrationStatus> response = migrationController.triggerMigration();

        // Then: Response should be OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // And: Migration should be triggered
        verify(migrationJob, times(1)).migrateSecrets();
        
        // And: Status should be retrieved twice (before and after)
        verify(migrationJob, times(2)).getStatus();
        
        // And: Response should contain updated status
        assertThat(response.getBody()).isEqualTo(afterStatus);
    }

    /**
     * Test that trigger endpoint returns updated status after migration.
     * 
     * <p>The response should reflect the results of the migration that just ran.</p>
     */
    @Test
    void shouldReturnUpdatedStatusAfterTrigger() {
        // Given: Migration will complete successfully
        MigrationStatus beforeStatus = createTestStatus(false);
        beforeStatus.setMigrated(0);
        beforeStatus.setFailed(0);
        
        MigrationStatus afterStatus = createTestStatus(false);
        afterStatus.setMigrated(100);
        afterStatus.setFailed(5);
        afterStatus.setLastRun(Instant.now());
        
        when(migrationJob.getStatus())
            .thenReturn(beforeStatus)
            .thenReturn(afterStatus);

        // When: Trigger endpoint is called
        ResponseEntity<MigrationStatus> response = migrationController.triggerMigration();

        // Then: Response should show migration results
        MigrationStatus body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getMigrated()).isEqualTo(100);
        assertThat(body.getFailed()).isEqualTo(5);
        assertThat(body.getLastRun()).isNotNull();
    }

    // ========== Concurrent Request Handling Tests ==========

    /**
     * Test that concurrent trigger requests do not start multiple migrations.
     * 
     * <p>When a migration is already in progress, the trigger endpoint should
     * return the current status without starting a new migration.</p>
     */
    @Test
    void shouldNotStartMigrationWhenAlreadyInProgress() {
        // Given: Migration is already in progress
        MigrationStatus inProgressStatus = createTestStatus(true);
        when(migrationJob.getStatus()).thenReturn(inProgressStatus);

        // When: Trigger endpoint is called
        ResponseEntity<MigrationStatus> response = migrationController.triggerMigration();

        // Then: Response should be OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // And: Migration should NOT be triggered
        verify(migrationJob, never()).migrateSecrets();
        
        // And: Status should be retrieved once
        verify(migrationJob, times(1)).getStatus();
        
        // And: Response should indicate migration in progress
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isInProgress()).isTrue();
    }

    /**
     * Test that multiple concurrent trigger requests are handled correctly.
     * 
     * <p>Simulates multiple requests arriving while migration is in progress.</p>
     */
    @Test
    void shouldHandleMultipleConcurrentTriggerRequests() {
        // Given: Migration is in progress
        MigrationStatus inProgressStatus = createTestStatus(true);
        when(migrationJob.getStatus()).thenReturn(inProgressStatus);

        // When: Multiple trigger requests are made
        ResponseEntity<MigrationStatus> response1 = migrationController.triggerMigration();
        ResponseEntity<MigrationStatus> response2 = migrationController.triggerMigration();
        ResponseEntity<MigrationStatus> response3 = migrationController.triggerMigration();

        // Then: All responses should be OK
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // And: Migration should never be triggered
        verify(migrationJob, never()).migrateSecrets();
        
        // And: All responses should indicate in progress
        assertThat(response1.getBody().isInProgress()).isTrue();
        assertThat(response2.getBody().isInProgress()).isTrue();
        assertThat(response3.getBody().isInProgress()).isTrue();
    }

    /**
     * Test that trigger request after completion starts new migration.
     * 
     * <p>After a migration completes, a new trigger request should start
     * a fresh migration.</p>
     */
    @Test
    void shouldStartNewMigrationAfterPreviousCompletes() {
        // Given: First migration completes, then trigger again
        MigrationStatus notInProgress = createTestStatus(false);
        MigrationStatus afterMigration = createTestStatus(false);
        afterMigration.setMigrated(50);
        
        when(migrationJob.getStatus())
            .thenReturn(notInProgress)   // First trigger: not in progress
            .thenReturn(afterMigration)  // First trigger: after migration
            .thenReturn(notInProgress)   // Second trigger: not in progress
            .thenReturn(afterMigration); // Second trigger: after migration

        // When: Trigger endpoint is called twice
        migrationController.triggerMigration();
        migrationController.triggerMigration();

        // Then: Migration should be triggered twice
        verify(migrationJob, times(2)).migrateSecrets();
    }

    // ========== HTTP Status Code Tests ==========

    /**
     * Test that status endpoint returns HTTP 200 OK.
     * 
     * <p>The status endpoint should always return 200 OK when successful.</p>
     */
    @Test
    void statusEndpointShouldReturnHttp200() {
        // Given: MigrationJob has a status
        when(migrationJob.getStatus()).thenReturn(testStatus);

        // When: Status endpoint is called
        ResponseEntity<MigrationStatus> response = migrationController.getStatus();

        // Then: HTTP status should be 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    /**
     * Test that trigger endpoint returns HTTP 200 OK when starting migration.
     * 
     * <p>The trigger endpoint should return 200 OK when successfully starting
     * a migration.</p>
     */
    @Test
    void triggerEndpointShouldReturnHttp200WhenStartingMigration() {
        // Given: No migration in progress
        MigrationStatus status = createTestStatus(false);
        when(migrationJob.getStatus()).thenReturn(status);

        // When: Trigger endpoint is called
        ResponseEntity<MigrationStatus> response = migrationController.triggerMigration();

        // Then: HTTP status should be 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    /**
     * Test that trigger endpoint returns HTTP 200 OK when migration in progress.
     * 
     * <p>The trigger endpoint should return 200 OK even when a migration is
     * already in progress (not an error condition).</p>
     */
    @Test
    void triggerEndpointShouldReturnHttp200WhenMigrationInProgress() {
        // Given: Migration is in progress
        MigrationStatus inProgressStatus = createTestStatus(true);
        when(migrationJob.getStatus()).thenReturn(inProgressStatus);

        // When: Trigger endpoint is called
        ResponseEntity<MigrationStatus> response = migrationController.triggerMigration();

        // Then: HTTP status should be 200 OK
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    // ========== Edge Case Tests ==========

    /**
     * Test that trigger endpoint handles migration with all failures.
     * 
     * <p>Even if all environments fail to migrate, the endpoint should
     * return successfully with the failure counts.</p>
     */
    @Test
    void shouldHandleMigrationWithAllFailures() {
        // Given: Migration will fail for all environments
        MigrationStatus beforeStatus = createTestStatus(false);
        MigrationStatus afterStatus = createTestStatus(false);
        afterStatus.setTotalEnvironments(10);
        afterStatus.setMigrated(0);
        afterStatus.setFailed(10);
        
        when(migrationJob.getStatus())
            .thenReturn(beforeStatus)
            .thenReturn(afterStatus);

        // When: Trigger endpoint is called
        ResponseEntity<MigrationStatus> response = migrationController.triggerMigration();

        // Then: Response should be OK with failure counts
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMigrated()).isZero();
        assertThat(response.getBody().getFailed()).isEqualTo(10);
    }

    /**
     * Test that trigger endpoint handles migration with no environments.
     * 
     * <p>Edge case: triggering migration when database is empty.</p>
     */
    @Test
    void shouldHandleMigrationWithNoEnvironments() {
        // Given: No environments in database
        MigrationStatus beforeStatus = createTestStatus(false);
        beforeStatus.setTotalEnvironments(0);
        
        MigrationStatus afterStatus = createTestStatus(false);
        afterStatus.setTotalEnvironments(0);
        afterStatus.setMigrated(0);
        afterStatus.setFailed(0);
        
        when(migrationJob.getStatus())
            .thenReturn(beforeStatus)
            .thenReturn(afterStatus);

        // When: Trigger endpoint is called
        ResponseEntity<MigrationStatus> response = migrationController.triggerMigration();

        // Then: Response should be OK with zero counts
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTotalEnvironments()).isZero();
        assertThat(response.getBody().getMigrated()).isZero();
        assertThat(response.getBody().getFailed()).isZero();
        
        // And: Migration should still be triggered
        verify(migrationJob, times(1)).migrateSecrets();
    }

    /**
     * Test that status endpoint can be called multiple times.
     * 
     * <p>The status endpoint should be idempotent and callable multiple times.</p>
     */
    @Test
    void shouldAllowMultipleStatusRequests() {
        // Given: MigrationJob has a status
        when(migrationJob.getStatus()).thenReturn(testStatus);

        // When: Status endpoint is called multiple times
        ResponseEntity<MigrationStatus> response1 = migrationController.getStatus();
        ResponseEntity<MigrationStatus> response2 = migrationController.getStatus();
        ResponseEntity<MigrationStatus> response3 = migrationController.getStatus();

        // Then: All responses should be OK
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response3.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // And: Status should be retrieved 3 times
        verify(migrationJob, times(3)).getStatus();
    }

    /**
     * Test that trigger endpoint executes migration synchronously.
     * 
     * <p>The trigger endpoint should wait for migration to complete before
     * returning the updated status.</p>
     */
    @Test
    void shouldExecuteMigrationSynchronously() {
        // Given: Migration will complete
        MigrationStatus beforeStatus = createTestStatus(false);
        MigrationStatus afterStatus = createTestStatus(false);
        afterStatus.setMigrated(10);
        
        when(migrationJob.getStatus())
            .thenReturn(beforeStatus)
            .thenReturn(afterStatus);

        // When: Trigger endpoint is called
        migrationController.triggerMigration();

        // Then: migrateSecrets should be called before second getStatus
        verify(migrationJob, times(1)).migrateSecrets();
        verify(migrationJob, times(2)).getStatus();
    }

    // ========== Helper Methods ==========

    /**
     * Creates a test MigrationStatus with default values.
     * 
     * @param inProgress whether migration is in progress
     * @return test MigrationStatus
     */
    private MigrationStatus createTestStatus(boolean inProgress) {
        MigrationStatus status = new MigrationStatus();
        status.setLastRun(Instant.parse("2024-01-15T02:00:00Z"));
        status.setTotalEnvironments(100);
        status.setMigrated(95);
        status.setFailed(5);
        status.setCurrentKeyVersion("20240115");
        status.setInProgress(inProgress);
        return status;
    }
}
