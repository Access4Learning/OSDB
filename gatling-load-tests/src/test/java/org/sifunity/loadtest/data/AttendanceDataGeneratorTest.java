// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AttendanceDataGenerator}.
 * Verifies single-record generation and the edge case where recordCount=0
 * produces an empty array inside a valid SIF envelope.
 */
class AttendanceDataGeneratorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void singleRecordProducesValidEnvelopeWithAllFields() throws Exception {
        String json = AttendanceDataGenerator.generateEventPayload(1);
        JsonNode root = MAPPER.readTree(json);

        // Envelope structure
        assertTrue(root.has("StudentDailyAttendances"), "Root must have StudentDailyAttendances");
        JsonNode collection = root.get("StudentDailyAttendances");
        assertTrue(collection.has("StudentDailyAttendance"), "Collection must have StudentDailyAttendance array");
        JsonNode array = collection.get("StudentDailyAttendance");
        assertTrue(array.isArray(), "StudentDailyAttendance must be an array");
        assertEquals(1, array.size(), "Array must contain exactly 1 record");

        // All expected fields present
        JsonNode record = array.get(0);
        assertNotNull(record.get("RefId"), "RefId must be present");
        assertNotNull(record.get("StudentPersonalRefId"), "StudentPersonalRefId must be present");
        assertNotNull(record.get("SchoolInfoRefId"), "SchoolInfoRefId must be present");
        assertNotNull(record.get("Date"), "Date must be present");
        assertNotNull(record.get("SchoolYear"), "SchoolYear must be present");
        assertNotNull(record.get("AttendanceCodeInfoRefId"), "AttendanceCodeInfoRefId must be present");
        assertNotNull(record.get("TimeIn"), "TimeIn must be present");
        assertNotNull(record.get("TimeOut"), "TimeOut must be present");
        assertNotNull(record.get("AttendanceNote"), "AttendanceNote must be present");

        // RefId format: 32-char uppercase hex
        assertTrue(record.get("RefId").asText().matches("[A-F0-9]{32}"),
                "RefId must be 32-char uppercase hex");

        // Date format: YYYY-MM-DD
        assertTrue(record.get("Date").asText().matches("\\d{4}-\\d{2}-\\d{2}"),
                "Date must be YYYY-MM-DD");

        // SchoolYear: 4-digit year
        assertTrue(record.get("SchoolYear").asText().matches("\\d{4}"),
                "SchoolYear must be 4-digit year");

        // Time format: HH:mm:ss
        assertTrue(record.get("TimeIn").asText().matches("\\d{2}:\\d{2}:\\d{2}"),
                "TimeIn must be HH:mm:ss");
        assertTrue(record.get("TimeOut").asText().matches("\\d{2}:\\d{2}:\\d{2}"),
                "TimeOut must be HH:mm:ss");
    }

    @Test
    void zeroRecordsProducesEmptyArrayInValidEnvelope() throws Exception {
        String json = AttendanceDataGenerator.generateEventPayload(0);
        JsonNode root = MAPPER.readTree(json);

        assertTrue(root.has("StudentDailyAttendances"), "Root must have StudentDailyAttendances");
        JsonNode collection = root.get("StudentDailyAttendances");
        assertTrue(collection.has("StudentDailyAttendance"), "Collection must have StudentDailyAttendance array");
        JsonNode array = collection.get("StudentDailyAttendance");
        assertTrue(array.isArray(), "StudentDailyAttendance must be an array");
        assertEquals(0, array.size(), "Array must be empty for recordCount=0");
    }

    @Test
    void randomHex32ProducesCorrectFormat() {
        String hex = AttendanceDataGenerator.randomHex32();
        assertEquals(32, hex.length(), "Hex string must be 32 characters");
        assertTrue(hex.matches("[A-F0-9]{32}"), "Must be uppercase hex only");
    }

    @Test
    void multipleRecordsProducesCorrectCount() throws Exception {
        String json = AttendanceDataGenerator.generateEventPayload(5);
        JsonNode root = MAPPER.readTree(json);
        JsonNode array = root.get("StudentDailyAttendances").get("StudentDailyAttendance");
        assertEquals(5, array.size(), "Array must contain exactly 5 records");
    }
}
