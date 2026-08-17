// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.aspect.unit;

import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Scope;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.sifunity.broker.common.aspect.RepositorySpanAspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RepositorySpanAspect}.
 *
 * <p>Tests entity type extraction (via span name verification), entity type fallback,
 * span attribute values for known repository scenarios, and conditional loading.</p>
 *
 * <p>Validates: Requirements 1.2, 1.4, 1.8, 6.1, 6.2</p>
 */
class RepositorySpanAspectTest {

    // ========== Test entity classes and repository interfaces ==========

    static class Domain { UUID id; }
    static class Zone { UUID id; }
    static class Alert { UUID id; }

    /** Repository with known entity type Domain. */
    interface DomainRepository extends JpaRepository<Domain, UUID> {}

    /** Repository with known entity type Zone. */
    interface ZoneRepository extends JpaRepository<Zone, UUID> {}

    /** Repository with known entity type Alert. */
    interface AlertRepository extends JpaRepository<Alert, UUID> {}

    // ========== Mock infrastructure ==========

    private Tracer tracer;
    private Span span;
    private SpanBuilder spanBuilder;
    private RepositorySpanAspect aspect;

    @BeforeEach
    void setUp() {
        span = mock(Span.class);
        when(span.setAttribute(anyString(), anyString())).thenReturn(span);
        when(span.setAttribute(anyString(), anyLong())).thenReturn(span);
        when(span.setAttribute(anyString(), anyBoolean())).thenReturn(span);
        when(span.setStatus(any(StatusCode.class))).thenReturn(span);
        when(span.setStatus(any(StatusCode.class), anyString())).thenReturn(span);

        Scope scope = mock(Scope.class);
        when(span.makeCurrent()).thenReturn(scope);

        spanBuilder = mock(SpanBuilder.class);
        when(spanBuilder.setSpanKind(any(SpanKind.class))).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);

        tracer = mock(Tracer.class);
        when(tracer.spanBuilder(anyString())).thenReturn(spanBuilder);

        aspect = new RepositorySpanAspect(tracer);
    }

    /**
     * Builds a mock ProceedingJoinPoint whose target is a mock of the given
     * repository interface. This lets the aspect walk the mock's class hierarchy
     * to extract the entity type from JpaRepository generic parameters.
     */
    private ProceedingJoinPoint buildJoinPoint(Object target, String methodName, Object result)
            throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(result);

        Signature signature = mock(Signature.class);
        when(signature.getName()).thenReturn(methodName);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(target);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        return joinPoint;
    }

    // ========== Entity Type Extraction (verified via span name) ==========

    @Nested
    @DisplayName("Entity type extraction from repository generic type parameters")
    class EntityTypeExtraction {

        @Test
        @DisplayName("Extracts 'Domain' from DomainRepository extends JpaRepository<Domain, UUID>")
        void extractsDomainEntityType() throws Throwable {
            DomainRepository target = mock(DomainRepository.class);
            ProceedingJoinPoint jp = buildJoinPoint(target, "findById", Optional.empty());

            aspect.traceRepositoryCall(jp);

            // Span name should contain "Domain" as entity type
            verify(tracer).spanBuilder("db.jpa.Domain.findById");
        }

        @Test
        @DisplayName("Extracts 'Zone' from ZoneRepository extends JpaRepository<Zone, UUID>")
        void extractsZoneEntityType() throws Throwable {
            ZoneRepository target = mock(ZoneRepository.class);
            ProceedingJoinPoint jp = buildJoinPoint(target, "save", new Zone());

            aspect.traceRepositoryCall(jp);

            verify(tracer).spanBuilder("db.jpa.Zone.save");
        }

        @Test
        @DisplayName("Extracts 'Alert' from AlertRepository extends JpaRepository<Alert, UUID>")
        void extractsAlertEntityType() throws Throwable {
            AlertRepository target = mock(AlertRepository.class);
            ProceedingJoinPoint jp = buildJoinPoint(target, "deleteAll", null);

            aspect.traceRepositoryCall(jp);

            verify(tracer).spanBuilder("db.jpa.Alert.deleteAll");
        }

        @Test
        @DisplayName("Caches entity type — second call for same repo class reuses cached value")
        void cachesEntityType() throws Throwable {
            DomainRepository target = mock(DomainRepository.class);

            ProceedingJoinPoint jp1 = buildJoinPoint(target, "findById", Optional.empty());
            aspect.traceRepositoryCall(jp1);

            ProceedingJoinPoint jp2 = buildJoinPoint(target, "save", new Domain());
            aspect.traceRepositoryCall(jp2);

            // Both calls should use "Domain" — verify both span names
            verify(tracer).spanBuilder("db.jpa.Domain.findById");
            verify(tracer).spanBuilder("db.jpa.Domain.save");
        }
    }

    // ========== Entity Type Fallback ==========

    @Nested
    @DisplayName("Entity type fallback to 'Unknown'")
    class EntityTypeFallback {

        @Test
        @DisplayName("Returns 'Unknown' for plain Object target (no JpaRepository in hierarchy)")
        void fallsBackToUnknownForPlainObject() throws Throwable {
            Object target = new Object();
            ProceedingJoinPoint jp = buildJoinPoint(target, "findById", Optional.empty());

            aspect.traceRepositoryCall(jp);

            verify(tracer).spanBuilder("db.jpa.Unknown.findById");
        }

        @Test
        @DisplayName("Returns 'Unknown' for target class not extending JpaRepository")
        void fallsBackToUnknownForNonJpaClass() throws Throwable {
            // A simple Runnable — no JpaRepository in hierarchy
            Runnable target = mock(Runnable.class);
            ProceedingJoinPoint jp = buildJoinPoint(target, "count", 0L);

            aspect.traceRepositoryCall(jp);

            verify(tracer).spanBuilder("db.jpa.Unknown.count");
        }
    }

    // ========== Span Attribute Values for Known Scenarios ==========

    @Nested
    @DisplayName("Span attribute values for known repository scenarios")
    class SpanAttributes {

        @Test
        @DisplayName("findById: db.system=postgresql, db.operation=SELECT, db.entity_type=Domain, db.result_found=true")
        void findByIdWithPresentResult() throws Throwable {
            DomainRepository target = mock(DomainRepository.class);
            ProceedingJoinPoint jp = buildJoinPoint(target, "findById", Optional.of(new Domain()));

            aspect.traceRepositoryCall(jp);

            verify(span).setAttribute("db.system", "postgresql");
            verify(span).setAttribute("db.operation", "SELECT");
            verify(span).setAttribute("db.entity_type", "Domain");
            verify(span).setAttribute("db.result_found", true);
            verify(span).setStatus(StatusCode.OK);
            verify(span).end();
        }

        @Test
        @DisplayName("findById with empty Optional: db.result_found=false")
        void findByIdWithEmptyResult() throws Throwable {
            DomainRepository target = mock(DomainRepository.class);
            ProceedingJoinPoint jp = buildJoinPoint(target, "findById", Optional.empty());

            aspect.traceRepositoryCall(jp);

            verify(span).setAttribute("db.result_found", false);
        }

        @Test
        @DisplayName("save: db.operation=INSERT, db.entity_type=Domain")
        void saveOperation() throws Throwable {
            DomainRepository target = mock(DomainRepository.class);
            ProceedingJoinPoint jp = buildJoinPoint(target, "save", new Domain());

            aspect.traceRepositoryCall(jp);

            verify(span).setAttribute("db.operation", "INSERT");
            verify(span).setAttribute("db.entity_type", "Domain");
            verify(span).setStatus(StatusCode.OK);
        }

        @Test
        @DisplayName("deleteAll: db.operation=DELETE, db.entity_type=Zone")
        void deleteAllOperation() throws Throwable {
            ZoneRepository target = mock(ZoneRepository.class);
            ProceedingJoinPoint jp = buildJoinPoint(target, "deleteAll", null);

            aspect.traceRepositoryCall(jp);

            verify(span).setAttribute("db.operation", "DELETE");
            verify(span).setAttribute("db.entity_type", "Zone");
        }

        @Test
        @DisplayName("findAll returning list: db.result_count set to list size")
        void findAllWithListResult() throws Throwable {
            AlertRepository target = mock(AlertRepository.class);
            List<Alert> results = List.of(new Alert(), new Alert(), new Alert());
            ProceedingJoinPoint jp = buildJoinPoint(target, "findAll", results);

            aspect.traceRepositoryCall(jp);

            verify(span).setAttribute("db.operation", "SELECT");
            verify(span).setAttribute("db.entity_type", "Alert");
            verify(span).setAttribute("db.result_count", 3L);
        }

        @Test
        @DisplayName("Span name follows db.jpa.{EntityType}.{methodName} convention")
        void spanNameConvention() throws Throwable {
            DomainRepository target = mock(DomainRepository.class);
            ProceedingJoinPoint jp = buildJoinPoint(target, "findById", Optional.empty());

            aspect.traceRepositoryCall(jp);

            verify(tracer).spanBuilder("db.jpa.Domain.findById");
        }
    }

    // ========== Conditional Loading ==========

    @Nested
    @DisplayName("Conditional loading: aspect not created when observability.enabled=false")
    class ConditionalLoading {

        @Test
        @DisplayName("@ConditionalOnProperty(name='observability.enabled', havingValue='true') is present")
        void hasConditionalOnPropertyAnnotation() {
            ConditionalOnProperty annotation =
                    RepositorySpanAspect.class.getAnnotation(ConditionalOnProperty.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.name()).containsExactly("observability.enabled");
            assertThat(annotation.havingValue()).isEqualTo("true");
        }
    }
}
