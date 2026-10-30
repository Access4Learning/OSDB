// SPDX-License-Identifier: MIT
package org.sifunity.loadtest.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.Year;
import java.util.Random;
import java.util.UUID;

/**
 * Generates StudentDailyAttendance JSON payloads wrapped in the SIF collection
 * envelope: {@code {"StudentDailyAttendances":{"StudentDailyAttendance":[...]}}}.
 * <p>
 * Uses {@link ThreadLocal} {@link Random} to avoid contention in multi-threaded
 * Gatling execution. RefIds are 32-character uppercase hex strings derived from
 * random UUIDs.
 */
public final class AttendanceDataGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ThreadLocal<Random> RNG = ThreadLocal.withInitial(Random::new);

    private AttendanceDataGenerator() { }

    /**
     * Generate a SIF collection envelope with {@code recordCount}
     * StudentDailyAttendance records.
     *
     * @param recordCount number of records (0 produces an empty array)
     * @return JSON string with SIF envelope structure
     */
    public static String generateEventPayload(int recordCount) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            ObjectNode collection = root.putObject("StudentDailyAttendances");
            ArrayNode array = collection.putArray("StudentDailyAttendance");

            for (int i = 0; i < recordCount; i++) {
                array.add(generateRecord());
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate event payload", e);
        }
    }

    /**
     * Generate a single StudentDailyAttendance record as an ObjectNode.
     * Package-private for direct testing.
     */
    static ObjectNode generateRecord() {
        String date = LocalDate.now().toString();               // YYYY-MM-DD
        String schoolYear = String.valueOf(Year.now().getValue()); // 4-digit year

        int timeInHour = 7 + RNG.get().nextInt(3);              // 07-09
        int timeOutHour = timeInHour + 1 + RNG.get().nextInt(6); // at least 1h later, max 15
        int timeInMin = RNG.get().nextInt(60);
        int timeOutMin = RNG.get().nextInt(60);

        ObjectNode record = MAPPER.createObjectNode();
        record.put("RefId", randomHex32());
        record.put("StudentPersonalRefId", randomHex32());
        record.put("SchoolInfoRefId", randomHex32());
        record.put("Date", date);
        record.put("SchoolYear", schoolYear);
        record.put("AttendanceCodeInfoRefId", randomHex32());
        record.put("TimeIn", String.format("%02d:%02d:00", timeInHour, timeInMin));
        record.put("TimeOut", String.format("%02d:%02d:00", timeOutHour, timeOutMin));
        record.put("AttendanceNote", "Load test record");
        return record;
    }

    /**
     * 32-character uppercase hex string from a random UUID.
     * Package-private for direct testing.
     */
    static String randomHex32() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
