// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Property-based tests for {@link AttendanceDataGenerator}.
 * <p>
 * Property 3: SIF envelope structure and record count.
 * Property 4: StudentDailyAttendance generator field validity invariants.
 *
 * Validates: Requirements 8.3, 8.4, 8.5, 11.1, 11.2, 11.3, 11.4, 11.5, 11.6
 */
class AttendanceDataGeneratorPropertyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Property 3: For any n (1..100), generateEventPayload(n) produces valid JSON
     * with structure {"StudentDailyAttendances":{"StudentDailyAttendance":[...]}}
     * where the inner array has exactly n elements.
     *
     * **Validates: Requirements 8.3, 8.4, 11.6**
     */
    @Property(tries = 100)
    @Tag("Feature: gatling-load-tests, Property 3: SIF envelope structure and record count")
    void envelopeStructureAndRecordCount(
            @ForAll("recordCounts") int n
    ) throws Exception {
        String json = AttendanceDataGenerator.generateEventPayload(n);

        // Must be valid JSON
        JsonNode root = MAPPER.readTree(json);
        assertNotNull(root, "Payload must be valid JSON");

        // Must have top-level "StudentDailyAttendances" object
        assertTrue(root.has("StudentDailyAttendances"),
                "Root must have 'StudentDailyAttendances' key");
        JsonNode collection = root.get("StudentDailyAttendances");
        assertTrue(collection.isObject(),
                "'StudentDailyAttendances' must be an object");

        // Must have "StudentDailyAttendance" array inside
        assertTrue(collection.has("StudentDailyAttendance"),
                "Collection must have 'StudentDailyAttendance' key");
        JsonNode array = collection.get("StudentDailyAttendance");
        assertTrue(array.isArray(),
                "'StudentDailyAttendance' must be an array");

        // Array must have exactly n elements
        assertEquals(n, array.size(),
                "Array must contain exactly " + n + " records, got " + array.size());
    }

    /**
     * Property 4: For any generated record batch, all field validity invariants hold:
     * - RefId, StudentPersonalRefId, SchoolInfoRefId, AttendanceCodeInfoRefId match [A-F0-9]{32}
     * - Date matches \d{4}-\d{2}-\d{2} and is a valid date
     * - SchoolYear matches \d{4}
     * - TimeIn matches \d{2}:\d{2}:\d{2} and is a valid time
     * - TimeOut matches \d{2}:\d{2}:\d{2} and is a valid time
     * - TimeIn < TimeOut
     * - All RefId values within a single batch are unique
     *
     * **Validates: Requirements 8.5, 11.1, 11.2, 11.3, 11.4, 11.5**
     */
    @Property(tries = 100)
    @Tag("Feature: gatling-load-tests, Property 4: StudentDailyAttendance generator validity")
    void fieldValidityInvariants(
            @ForAll("recordCounts") int n
    ) throws Exception {
        String json = AttendanceDataGenerator.generateEventPayload(n);
        JsonNode root = MAPPER.readTree(json);
        JsonNode array = root.get("StudentDailyAttendances").get("StudentDailyAttendance");

        Set<String> allRefIds = new HashSet<>();

        for (int i = 0; i < array.size(); i++) {
            JsonNode record = array.get(i);

            // --- Hex RefId fields ---
            String refId = record.get("RefId").asText();
            String studentRefId = record.get("StudentPersonalRefId").asText();
            String schoolRefId = record.get("SchoolInfoRefId").asText();
            String attendanceCodeRefId = record.get("AttendanceCodeInfoRefId").asText();

            assertTrue(refId.matches("[A-F0-9]{32}"),
                    "RefId must be 32-char uppercase hex, got: " + refId);
            assertTrue(studentRefId.matches("[A-F0-9]{32}"),
                    "StudentPersonalRefId must be 32-char uppercase hex, got: " + studentRefId);
            assertTrue(schoolRefId.matches("[A-F0-9]{32}"),
                    "SchoolInfoRefId must be 32-char uppercase hex, got: " + schoolRefId);
            assertTrue(attendanceCodeRefId.matches("[A-F0-9]{32}"),
                    "AttendanceCodeInfoRefId must be 32-char uppercase hex, got: " + attendanceCodeRefId);

            // Collect all RefIds for uniqueness check
            allRefIds.add(refId);
            allRefIds.add(studentRefId);
            allRefIds.add(schoolRefId);
            allRefIds.add(attendanceCodeRefId);

            // --- Date field ---
            String date = record.get("Date").asText();
            assertTrue(date.matches("\\d{4}-\\d{2}-\\d{2}"),
                    "Date must match YYYY-MM-DD, got: " + date);
            assertDoesNotThrow(() -> LocalDate.parse(date),
                    "Date must be a valid date: " + date);

            // --- SchoolYear field ---
            String schoolYear = record.get("SchoolYear").asText();
            assertTrue(schoolYear.matches("\\d{4}"),
                    "SchoolYear must be 4-digit year, got: " + schoolYear);

            // --- TimeIn field ---
            String timeIn = record.get("TimeIn").asText();
            assertTrue(timeIn.matches("\\d{2}:\\d{2}:\\d{2}"),
                    "TimeIn must match HH:mm:ss, got: " + timeIn);
            LocalTime parsedTimeIn = assertDoesNotThrow(() -> LocalTime.parse(timeIn),
                    "TimeIn must be a valid time: " + timeIn);

            // --- TimeOut field ---
            String timeOut = record.get("TimeOut").asText();
            assertTrue(timeOut.matches("\\d{2}:\\d{2}:\\d{2}"),
                    "TimeOut must match HH:mm:ss, got: " + timeOut);
            LocalTime parsedTimeOut = assertDoesNotThrow(() -> LocalTime.parse(timeOut),
                    "TimeOut must be a valid time: " + timeOut);

            // --- TimeIn < TimeOut ---
            assertTrue(parsedTimeIn.isBefore(parsedTimeOut),
                    "TimeIn (" + timeIn + ") must be before TimeOut (" + timeOut + ")");
        }

        // All RefId values within the batch must be unique
        // Total expected: 4 RefIds per record × n records
        assertEquals(4 * n, allRefIds.size(),
                "All RefId values within a batch must be unique. Expected "
                        + (4 * n) + " unique RefIds, got " + allRefIds.size());
    }

    @Provide
    Arbitrary<Integer> recordCounts() {
        return Arbitraries.integers().between(1, 100);
    }
}
