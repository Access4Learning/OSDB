// SPDX-License-Identifier: MIT
package org.sifunity.broker.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JacksonConfiguration.
 * Tests ObjectMapper configuration for camelCase naming, date serialization, null handling,
 * unknown property handling, and Java 8 date/time support.
 */
class JacksonConfigurationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        JacksonConfiguration config = new JacksonConfiguration();
        objectMapper = config.objectMapper();
    }

    // ========== ObjectMapper Configuration Tests ==========

    @Test
    void objectMapper_isNotNull() {
        assertThat(objectMapper).isNotNull();
    }

    @Test
    void objectMapper_hasCamelCasePropertyNaming() {
        assertThat(objectMapper.getPropertyNamingStrategy())
            .isEqualTo(PropertyNamingStrategies.LOWER_CAMEL_CASE);
    }

    @Test
    void objectMapper_hasNonNullSerializationInclusion() {
        assertThat(objectMapper.getSerializationConfig().getDefaultPropertyInclusion().getValueInclusion())
            .isEqualTo(JsonInclude.Include.NON_NULL);
    }

    @Test
    void objectMapper_doesNotWriteDatesAsTimestamps() {
        assertThat(objectMapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS))
            .isFalse();
    }

    @Test
    void objectMapper_failsOnUnknownProperties() {
        assertThat(objectMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES))
            .isTrue();
    }

    // ========== CamelCase Property Naming Tests ==========

    @Test
    void camelCaseNaming_serializesFieldsInCamelCase() throws JsonProcessingException {
        TestObject obj = new TestObject("John", "Doe", 30);
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"firstName\"");
        assertThat(json).contains("\"lastName\"");
        assertThat(json).contains("\"ageInYears\"");
        assertThat(json).doesNotContain("first_name");
        assertThat(json).doesNotContain("last_name");
        assertThat(json).doesNotContain("age_in_years");
    }

    @Test
    void camelCaseNaming_deserializesFromCamelCase() throws JsonProcessingException {
        String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"ageInYears\":30}";
        TestObject obj = objectMapper.readValue(json, TestObject.class);
        
        assertThat(obj.firstName).isEqualTo("John");
        assertThat(obj.lastName).isEqualTo("Doe");
        assertThat(obj.ageInYears).isEqualTo(30);
    }

    @Test
    void camelCaseNaming_handlesSingleWordFields() throws JsonProcessingException {
        SingleWordObject obj = new SingleWordObject("test", 123);
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"name\"");
        assertThat(json).contains("\"value\"");
    }

    @Test
    void camelCaseNaming_handlesAcronyms() throws JsonProcessingException {
        AcronymObject obj = new AcronymObject("ABC123", "http://example.com");
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"id\"");
        assertThat(json).contains("\"url\"");
    }

    // ========== Date Serialization Format Tests ==========

    @Test
    void dateSerialization_serializesInstantAsISO8601() throws JsonProcessingException {
        Instant instant = Instant.parse("2024-01-15T10:30:00Z");
        DateObject obj = new DateObject(instant, null, null);
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"instant\":\"2024-01-15T10:30:00Z\"");
        assertThat(json).doesNotContain("1705316400000"); // Not a timestamp
    }

    @Test
    void dateSerialization_deserializesInstantFromISO8601() throws JsonProcessingException {
        String json = "{\"instant\":\"2024-01-15T10:30:00Z\"}";
        DateObject obj = objectMapper.readValue(json, DateObject.class);
        
        assertThat(obj.instant).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
    }

    @Test
    void dateSerialization_serializesLocalDateTimeAsISO8601() throws JsonProcessingException {
        LocalDateTime dateTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        DateObject obj = new DateObject(null, dateTime, null);
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"localDateTime\":\"2024-01-15T10:30:00\"");
    }

    @Test
    void dateSerialization_deserializesLocalDateTimeFromISO8601() throws JsonProcessingException {
        String json = "{\"localDateTime\":\"2024-01-15T10:30:00\"}";
        DateObject obj = objectMapper.readValue(json, DateObject.class);
        
        assertThat(obj.localDateTime).isEqualTo(LocalDateTime.of(2024, 1, 15, 10, 30, 0));
    }

    @Test
    void dateSerialization_serializesLocalDateAsISO8601() throws JsonProcessingException {
        LocalDate date = LocalDate.of(2024, 1, 15);
        DateObject obj = new DateObject(null, null, date);
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"localDate\":\"2024-01-15\"");
    }

    @Test
    void dateSerialization_deserializesLocalDateFromISO8601() throws JsonProcessingException {
        String json = "{\"localDate\":\"2024-01-15\"}";
        DateObject obj = objectMapper.readValue(json, DateObject.class);
        
        assertThat(obj.localDate).isEqualTo(LocalDate.of(2024, 1, 15));
    }

    @Test
    void dateSerialization_handlesInstantWithMilliseconds() throws JsonProcessingException {
        Instant instant = Instant.parse("2024-01-15T10:30:00.123Z");
        DateObject obj = new DateObject(instant, null, null);
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"instant\":\"2024-01-15T10:30:00.123Z\"");
    }

    @Test
    void dateSerialization_deserializesInstantWithMilliseconds() throws JsonProcessingException {
        String json = "{\"instant\":\"2024-01-15T10:30:00.123Z\"}";
        DateObject obj = objectMapper.readValue(json, DateObject.class);
        
        assertThat(obj.instant).isEqualTo(Instant.parse("2024-01-15T10:30:00.123Z"));
    }

    @Test
    void dateSerialization_handlesInstantWithNanoseconds() throws JsonProcessingException {
        Instant instant = Instant.parse("2024-01-15T10:30:00.123456789Z");
        DateObject obj = new DateObject(instant, null, null);
        String json = objectMapper.writeValueAsString(obj);
        
        // Deserialize back to verify round-trip
        DateObject deserialized = objectMapper.readValue(json, DateObject.class);
        assertThat(deserialized.instant).isEqualTo(instant);
    }

    @Test
    void dateSerialization_handlesEpochInstant() throws JsonProcessingException {
        Instant instant = Instant.EPOCH;
        DateObject obj = new DateObject(instant, null, null);
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"instant\":\"1970-01-01T00:00:00Z\"");
    }

    @Test
    void dateSerialization_handlesFutureDate() throws JsonProcessingException {
        Instant instant = Instant.parse("2099-12-31T23:59:59Z");
        DateObject obj = new DateObject(instant, null, null);
        String json = objectMapper.writeValueAsString(obj);
        
        DateObject deserialized = objectMapper.readValue(json, DateObject.class);
        assertThat(deserialized.instant).isEqualTo(instant);
    }

    // ========== Null Value Handling Tests ==========

    @Test
    void nullHandling_excludesNullFieldsFromSerialization() throws JsonProcessingException {
        TestObject obj = new TestObject("John", null, 30);
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"firstName\":\"John\"");
        assertThat(json).contains("\"ageInYears\":30");
        assertThat(json).doesNotContain("lastName");
        assertThat(json).doesNotContain("null");
    }

    @Test
    void nullHandling_excludesAllNullFields() throws JsonProcessingException {
        TestObject obj = new TestObject(null, null, null);
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).isEqualTo("{}");
    }

    @Test
    void nullHandling_includesNonNullFields() throws JsonProcessingException {
        TestObject obj = new TestObject("John", "Doe", null);
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"firstName\":\"John\"");
        assertThat(json).contains("\"lastName\":\"Doe\"");
        assertThat(json).doesNotContain("ageInYears");
    }

    @Test
    void nullHandling_deserializesNullFieldsAsNull() throws JsonProcessingException {
        String json = "{\"firstName\":\"John\",\"ageInYears\":30}";
        TestObject obj = objectMapper.readValue(json, TestObject.class);
        
        assertThat(obj.firstName).isEqualTo("John");
        assertThat(obj.lastName).isNull();
        assertThat(obj.ageInYears).isEqualTo(30);
    }

    @Test
    void nullHandling_deserializesExplicitNullAsNull() throws JsonProcessingException {
        String json = "{\"firstName\":\"John\",\"lastName\":null,\"ageInYears\":30}";
        TestObject obj = objectMapper.readValue(json, TestObject.class);
        
        assertThat(obj.firstName).isEqualTo("John");
        assertThat(obj.lastName).isNull();
        assertThat(obj.ageInYears).isEqualTo(30);
    }

    @Test
    void nullHandling_excludesNullDatesFromSerialization() throws JsonProcessingException {
        DateObject obj = new DateObject(Instant.now(), null, null);
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"instant\"");
        assertThat(json).doesNotContain("localDateTime");
        assertThat(json).doesNotContain("localDate");
    }

    // ========== Unknown Property Handling Tests ==========

    @Test
    void unknownPropertyHandling_failsOnUnknownProperty() {
        String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"unknownField\":\"value\"}";
        
        assertThatThrownBy(() -> objectMapper.readValue(json, TestObject.class))
            .isInstanceOf(UnrecognizedPropertyException.class)
            .hasMessageContaining("unknownField");
    }

    @Test
    void unknownPropertyHandling_failsOnMultipleUnknownProperties() {
        String json = "{\"firstName\":\"John\",\"unknown1\":\"value1\",\"unknown2\":\"value2\"}";
        
        assertThatThrownBy(() -> objectMapper.readValue(json, TestObject.class))
            .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void unknownPropertyHandling_succeedsWithOnlyKnownProperties() throws JsonProcessingException {
        String json = "{\"firstName\":\"John\",\"lastName\":\"Doe\",\"ageInYears\":30}";
        
        TestObject obj = objectMapper.readValue(json, TestObject.class);
        assertThat(obj.firstName).isEqualTo("John");
        assertThat(obj.lastName).isEqualTo("Doe");
        assertThat(obj.ageInYears).isEqualTo(30);
    }

    @Test
    void unknownPropertyHandling_failsOnTypoInPropertyName() {
        String json = "{\"fistName\":\"John\",\"lastName\":\"Doe\"}"; // Typo: fistName instead of firstName
        
        assertThatThrownBy(() -> objectMapper.readValue(json, TestObject.class))
            .isInstanceOf(UnrecognizedPropertyException.class)
            .hasMessageContaining("fistName");
    }

    @Test
    void unknownPropertyHandling_failsOnCaseVariation() {
        String json = "{\"FirstName\":\"John\",\"lastName\":\"Doe\"}"; // Wrong case: FirstName instead of firstName
        
        assertThatThrownBy(() -> objectMapper.readValue(json, TestObject.class))
            .isInstanceOf(UnrecognizedPropertyException.class)
            .hasMessageContaining("FirstName");
    }

    @Test
    void unknownPropertyHandling_failsOnSnakeCaseProperty() {
        String json = "{\"first_name\":\"John\",\"last_name\":\"Doe\"}"; // Snake case instead of camelCase
        
        assertThatThrownBy(() -> objectMapper.readValue(json, TestObject.class))
            .isInstanceOf(UnrecognizedPropertyException.class);
    }

    // ========== Java 8 Date/Time Serialization Tests ==========

    @Test
    void java8DateTime_supportsInstant() throws JsonProcessingException {
        Instant instant = Instant.now();
        DateObject obj = new DateObject(instant, null, null);
        
        String json = objectMapper.writeValueAsString(obj);
        DateObject deserialized = objectMapper.readValue(json, DateObject.class);
        
        assertThat(deserialized.instant).isEqualTo(instant);
    }

    @Test
    void java8DateTime_supportsLocalDateTime() throws JsonProcessingException {
        LocalDateTime dateTime = LocalDateTime.now();
        DateObject obj = new DateObject(null, dateTime, null);
        
        String json = objectMapper.writeValueAsString(obj);
        DateObject deserialized = objectMapper.readValue(json, DateObject.class);
        
        assertThat(deserialized.localDateTime).isEqualTo(dateTime);
    }

    @Test
    void java8DateTime_supportsLocalDate() throws JsonProcessingException {
        LocalDate date = LocalDate.now();
        DateObject obj = new DateObject(null, null, date);
        
        String json = objectMapper.writeValueAsString(obj);
        DateObject deserialized = objectMapper.readValue(json, DateObject.class);
        
        assertThat(deserialized.localDate).isEqualTo(date);
    }

    @Test
    void java8DateTime_supportsAllDateTypesSimultaneously() throws JsonProcessingException {
        Instant instant = Instant.now();
        LocalDateTime dateTime = LocalDateTime.now();
        LocalDate date = LocalDate.now();
        DateObject obj = new DateObject(instant, dateTime, date);
        
        String json = objectMapper.writeValueAsString(obj);
        DateObject deserialized = objectMapper.readValue(json, DateObject.class);
        
        assertThat(deserialized.instant).isEqualTo(instant);
        assertThat(deserialized.localDateTime).isEqualTo(dateTime);
        assertThat(deserialized.localDate).isEqualTo(date);
    }

    @Test
    void java8DateTime_handlesMinInstant() throws JsonProcessingException {
        Instant instant = Instant.MIN;
        DateObject obj = new DateObject(instant, null, null);
        
        String json = objectMapper.writeValueAsString(obj);
        DateObject deserialized = objectMapper.readValue(json, DateObject.class);
        
        assertThat(deserialized.instant).isEqualTo(instant);
    }

    @Test
    void java8DateTime_handlesMaxInstant() throws JsonProcessingException {
        Instant instant = Instant.MAX;
        DateObject obj = new DateObject(instant, null, null);
        
        String json = objectMapper.writeValueAsString(obj);
        DateObject deserialized = objectMapper.readValue(json, DateObject.class);
        
        assertThat(deserialized.instant).isEqualTo(instant);
    }

    @Test
    void java8DateTime_handlesMinLocalDate() throws JsonProcessingException {
        LocalDate date = LocalDate.MIN;
        DateObject obj = new DateObject(null, null, date);
        
        String json = objectMapper.writeValueAsString(obj);
        DateObject deserialized = objectMapper.readValue(json, DateObject.class);
        
        assertThat(deserialized.localDate).isEqualTo(date);
    }

    @Test
    void java8DateTime_handlesMaxLocalDate() throws JsonProcessingException {
        LocalDate date = LocalDate.MAX;
        DateObject obj = new DateObject(null, null, date);
        
        String json = objectMapper.writeValueAsString(obj);
        DateObject deserialized = objectMapper.readValue(json, DateObject.class);
        
        assertThat(deserialized.localDate).isEqualTo(date);
    }

    // ========== Integration Tests ==========

    @Test
    void integration_serializesComplexObjectCorrectly() throws JsonProcessingException {
        ComplexObject obj = new ComplexObject(
            "test-id",
            "Test Name",
            Instant.parse("2024-01-15T10:30:00Z"),
            null,
            42
        );
        
        String json = objectMapper.writeValueAsString(obj);
        
        assertThat(json).contains("\"objectId\":\"test-id\"");
        assertThat(json).contains("\"objectName\":\"Test Name\"");
        assertThat(json).contains("\"createdAt\":\"2024-01-15T10:30:00Z\"");
        assertThat(json).contains("\"count\":42");
        assertThat(json).doesNotContain("updatedAt");
        assertThat(json).doesNotContain("null");
    }

    @Test
    void integration_deserializesComplexObjectCorrectly() throws JsonProcessingException {
        String json = "{\"objectId\":\"test-id\",\"objectName\":\"Test Name\"," +
                     "\"createdAt\":\"2024-01-15T10:30:00Z\",\"count\":42}";
        
        ComplexObject obj = objectMapper.readValue(json, ComplexObject.class);
        
        assertThat(obj.objectId).isEqualTo("test-id");
        assertThat(obj.objectName).isEqualTo("Test Name");
        assertThat(obj.createdAt).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"));
        assertThat(obj.updatedAt).isNull();
        assertThat(obj.count).isEqualTo(42);
    }

    @Test
    void integration_roundTripSerializationPreservesData() throws JsonProcessingException {
        ComplexObject original = new ComplexObject(
            "test-id",
            "Test Name",
            Instant.parse("2024-01-15T10:30:00Z"),
            Instant.parse("2024-01-16T10:30:00Z"),
            42
        );
        
        String json = objectMapper.writeValueAsString(original);
        ComplexObject deserialized = objectMapper.readValue(json, ComplexObject.class);
        
        assertThat(deserialized.objectId).isEqualTo(original.objectId);
        assertThat(deserialized.objectName).isEqualTo(original.objectName);
        assertThat(deserialized.createdAt).isEqualTo(original.createdAt);
        assertThat(deserialized.updatedAt).isEqualTo(original.updatedAt);
        assertThat(deserialized.count).isEqualTo(original.count);
    }

    // ========== Test Helper Classes ==========

    public static class TestObject {
        public String firstName;
        public String lastName;
        public Integer ageInYears;

        public TestObject() {}

        public TestObject(String firstName, String lastName, Integer ageInYears) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.ageInYears = ageInYears;
        }
    }

    public static class SingleWordObject {
        public String name;
        public Integer value;

        public SingleWordObject() {}

        public SingleWordObject(String name, Integer value) {
            this.name = name;
            this.value = value;
        }
    }

    public static class AcronymObject {
        public String id;
        public String url;

        public AcronymObject() {}

        public AcronymObject(String id, String url) {
            this.id = id;
            this.url = url;
        }
    }

    public static class DateObject {
        public Instant instant;
        public LocalDateTime localDateTime;
        public LocalDate localDate;

        public DateObject() {}

        public DateObject(Instant instant, LocalDateTime localDateTime, LocalDate localDate) {
            this.instant = instant;
            this.localDateTime = localDateTime;
            this.localDate = localDate;
        }
    }

    public static class ComplexObject {
        public String objectId;
        public String objectName;
        public Instant createdAt;
        public Instant updatedAt;
        public Integer count;

        public ComplexObject() {}

        public ComplexObject(String objectId, String objectName, Instant createdAt, 
                           Instant updatedAt, Integer count) {
            this.objectId = objectId;
            this.objectName = objectName;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
            this.count = count;
        }
    }
}
