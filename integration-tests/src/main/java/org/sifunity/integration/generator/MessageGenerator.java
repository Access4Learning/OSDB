// SPDX-License-Identifier: MIT
package org.sifunity.integration.generator;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generator for creating test messages during integration tests.
 * 
 * <p>This class provides utilities for:
 * <ul>
 *   <li>Generating SIF-compliant messages (XML or JSON)</li>
 *   <li>Generating SIF headers (messageId, zoneId, contextId, timestamp)</li>
 *   <li>Creating test payloads for various SIF objects</li>
 * </ul>
 * 
 * <p>Example usage:
 * <pre>
 * MessageGenerator generator = new MessageGenerator();
 * 
 * // Generate SIF headers
 * Map&lt;String, String&gt; headers = generator.generateSifHeaders(
 *     "test-zone",
 *     "DEFAULT"
 * );
 * 
 * // Generate a StudentPersonal message
 * String payload = generator.generateStudentPersonalJson(
 *     "12345",
 *     "John",
 *     "Doe"
 * );
 * 
 * // Generate a SchoolInfo message
 * String schoolPayload = generator.generateSchoolInfoJson(
 *     "school-001",
 *     "Test High School"
 * );
 * </pre>
 * 
 * @since 1.0.0
 */
@Slf4j
public class MessageGenerator {
    
    /**
     * Generates SIF-compliant headers for a message.
     * 
     * @param zoneId the zone ID
     * @param contextId the context ID
     * @return map of header names to values
     */
    public Map<String, String> generateSifHeaders(String zoneId, String contextId) {
        return generateSifHeaders(UUID.randomUUID().toString(), zoneId, contextId, Instant.now());
    }
    
    /**
     * Generates SIF-compliant headers for a message with a specific message ID.
     * 
     * @param messageId the message ID
     * @param zoneId the zone ID
     * @param contextId the context ID
     * @return map of header names to values
     */
    public Map<String, String> generateSifHeaders(String messageId, String zoneId, String contextId) {
        return generateSifHeaders(messageId, zoneId, contextId, Instant.now());
    }
    
    /**
     * Generates SIF-compliant headers for a message with all parameters.
     * 
     * @param messageId the message ID
     * @param zoneId the zone ID
     * @param contextId the context ID
     * @param timestamp the message timestamp
     * @return map of header names to values
     */
    public Map<String, String> generateSifHeaders(
        String messageId,
        String zoneId,
        String contextId,
        Instant timestamp
    ) {
        Map<String, String> headers = new HashMap<>();
        headers.put("messageId", messageId);
        headers.put("zoneId", zoneId);
        headers.put("contextId", contextId);
        headers.put("timestamp", timestamp.toString());
        headers.put("Content-Type", "application/json");
        
        log.debug("Generated SIF headers: messageId={}, zoneId={}, contextId={}, timestamp={}",
            messageId, zoneId, contextId, timestamp);
        
        return headers;
    }
    
    /**
     * Generates a unique message ID.
     * 
     * @return a unique message ID
     */
    public String generateMessageId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Generates a StudentPersonal JSON payload.
     * 
     * @param refId the student reference ID
     * @param firstName the student's first name
     * @param lastName the student's last name
     * @return JSON string representing a StudentPersonal object
     */
    public String generateStudentPersonalJson(String refId, String firstName, String lastName) {
        String json = String.format("""
            {
              "refId": "%s",
              "localId": "%s",
              "name": {
                "type": "LGL",
                "familyName": "%s",
                "givenName": "%s"
              },
              "demographics": {
                "birthDate": "2010-01-15"
              }
            }
            """, refId, refId, lastName, firstName);
        
        log.debug("Generated StudentPersonal JSON: refId={}, name={} {}",
            refId, firstName, lastName);
        
        return json;
    }
    
    /**
     * Generates a StudentPersonal JSON payload with default values.
     * 
     * @return JSON string representing a StudentPersonal object
     */
    public String generateStudentPersonalJson() {
        String refId = UUID.randomUUID().toString();
        return generateStudentPersonalJson(refId, "Test", "Student");
    }
    
    /**
     * Generates a SchoolInfo JSON payload.
     * 
     * @param refId the school reference ID
     * @param schoolName the school name
     * @return JSON string representing a SchoolInfo object
     */
    public String generateSchoolInfoJson(String refId, String schoolName) {
        String json = String.format("""
            {
              "refId": "%s",
              "localId": "%s",
              "schoolName": "%s",
              "schoolType": "High School",
              "operationalStatus": "Open"
            }
            """, refId, refId, schoolName);
        
        log.debug("Generated SchoolInfo JSON: refId={}, schoolName={}",
            refId, schoolName);
        
        return json;
    }
    
    /**
     * Generates a SchoolInfo JSON payload with default values.
     * 
     * @return JSON string representing a SchoolInfo object
     */
    public String generateSchoolInfoJson() {
        String refId = UUID.randomUUID().toString();
        return generateSchoolInfoJson(refId, "Test School");
    }
    
    /**
     * Generates a StaffPersonal JSON payload.
     * 
     * @param refId the staff reference ID
     * @param firstName the staff member's first name
     * @param lastName the staff member's last name
     * @return JSON string representing a StaffPersonal object
     */
    public String generateStaffPersonalJson(String refId, String firstName, String lastName) {
        String json = String.format("""
            {
              "refId": "%s",
              "localId": "%s",
              "name": {
                "type": "LGL",
                "familyName": "%s",
                "givenName": "%s"
              },
              "employmentStatus": "Active"
            }
            """, refId, refId, lastName, firstName);
        
        log.debug("Generated StaffPersonal JSON: refId={}, name={} {}",
            refId, firstName, lastName);
        
        return json;
    }
    
    /**
     * Generates a StaffPersonal JSON payload with default values.
     * 
     * @return JSON string representing a StaffPersonal object
     */
    public String generateStaffPersonalJson() {
        String refId = UUID.randomUUID().toString();
        return generateStaffPersonalJson(refId, "Test", "Staff");
    }
    
    /**
     * Generates a generic SIF object JSON payload.
     * 
     * @param objectType the SIF object type
     * @param refId the reference ID
     * @param additionalFields additional fields to include
     * @return JSON string representing a generic SIF object
     */
    public String generateGenericSifObjectJson(
        String objectType,
        String refId,
        Map<String, Object> additionalFields
    ) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append(String.format("  \"refId\": \"%s\",\n", refId));
        json.append(String.format("  \"objectType\": \"%s\"", objectType));
        
        if (additionalFields != null && !additionalFields.isEmpty()) {
            for (Map.Entry<String, Object> entry : additionalFields.entrySet()) {
                json.append(",\n");
                json.append(String.format("  \"%s\": ", entry.getKey()));
                
                if (entry.getValue() instanceof String) {
                    json.append(String.format("\"%s\"", entry.getValue()));
                } else if (entry.getValue() instanceof Number || entry.getValue() instanceof Boolean) {
                    json.append(entry.getValue());
                } else {
                    json.append(String.format("\"%s\"", entry.getValue().toString()));
                }
            }
        }
        
        json.append("\n}");
        
        log.debug("Generated generic SIF object JSON: objectType={}, refId={}",
            objectType, refId);
        
        return json.toString();
    }
    
    /**
     * Generates a simple JSON payload for testing.
     * 
     * @param data the data to include in the payload
     * @return JSON string
     */
    public String generateSimpleJson(Map<String, Object> data) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                json.append(",\n");
            }
            first = false;
            
            json.append(String.format("  \"%s\": ", entry.getKey()));
            
            if (entry.getValue() instanceof String) {
                json.append(String.format("\"%s\"", entry.getValue()));
            } else if (entry.getValue() instanceof Number || entry.getValue() instanceof Boolean) {
                json.append(entry.getValue());
            } else {
                json.append(String.format("\"%s\"", entry.getValue().toString()));
            }
        }
        
        json.append("\n}");
        
        return json.toString();
    }
    
    /**
     * Generates an event payload for testing.
     * 
     * @param eventAction the event action (CREATE, UPDATE, DELETE)
     * @param objectType the SIF object type
     * @param objectData the object data
     * @return JSON string representing an event
     */
    public String generateEventJson(String eventAction, String objectType, String objectData) {
        String json = String.format("""
            {
              "eventAction": "%s",
              "objectType": "%s",
              "object": %s
            }
            """, eventAction, objectType, objectData);
        
        log.debug("Generated event JSON: eventAction={}, objectType={}",
            eventAction, objectType);
        
        return json;
    }
    
    /**
     * Generates a CREATE event for a StudentPersonal.
     * 
     * @param refId the student reference ID
     * @param firstName the student's first name
     * @param lastName the student's last name
     * @return JSON string representing a CREATE event
     */
    public String generateStudentCreateEventJson(String refId, String firstName, String lastName) {
        String studentData = generateStudentPersonalJson(refId, firstName, lastName);
        return generateEventJson("CREATE", "StudentPersonal", studentData);
    }
    
    /**
     * Generates an UPDATE event for a StudentPersonal.
     * 
     * @param refId the student reference ID
     * @param firstName the student's first name
     * @param lastName the student's last name
     * @return JSON string representing an UPDATE event
     */
    public String generateStudentUpdateEventJson(String refId, String firstName, String lastName) {
        String studentData = generateStudentPersonalJson(refId, firstName, lastName);
        return generateEventJson("UPDATE", "StudentPersonal", studentData);
    }
    
    /**
     * Generates a DELETE event for a StudentPersonal.
     * 
     * @param refId the student reference ID
     * @return JSON string representing a DELETE event
     */
    public String generateStudentDeleteEventJson(String refId) {
        String studentData = String.format("{\"refId\": \"%s\"}", refId);
        return generateEventJson("DELETE", "StudentPersonal", studentData);
    }
    
    /**
     * Generates a StudentPersonal request payload.
     * 
     * <p>This is typically used for IMMEDIATE or DELAYED pattern requests
     * where a Consumer is requesting a StudentPersonal object.
     * 
     * @param refId the student reference ID to request
     * @return JSON string representing a request for a StudentPersonal
     */
    public String generateStudentPersonalRequest(String refId) {
        String json = String.format("""
            {
              "refId": "%s",
              "requestType": "GET"
            }
            """, refId);
        
        log.debug("Generated StudentPersonal request: refId={}", refId);
        
        return json;
    }
    
    /**
     * Generates a StudentPersonal response payload.
     * 
     * <p>This is typically used as the response from a Provider when
     * a Consumer requests a StudentPersonal object.
     * 
     * @param refId the student reference ID
     * @return JSON string representing a StudentPersonal response
     */
    public String generateStudentPersonalResponse(String refId) {
        return generateStudentPersonalJson(refId, "John", "Doe");
    }
    
    /**
     * Generates a SchoolInfo request payload.
     * 
     * @param refId the school reference ID to request
     * @return JSON string representing a request for a SchoolInfo
     */
    public String generateSchoolInfoRequest(String refId) {
        String json = String.format("""
            {
              "refId": "%s",
              "requestType": "GET"
            }
            """, refId);
        
        log.debug("Generated SchoolInfo request: refId={}", refId);
        
        return json;
    }
    
    /**
     * Generates a SchoolInfo response payload.
     * 
     * @param refId the school reference ID
     * @return JSON string representing a SchoolInfo response
     */
    public String generateSchoolInfoResponse(String refId) {
        return generateSchoolInfoJson(refId, "Test High School");
    }
    
    /**
     * Generates a response DTO for the Responses Connector API.
     * 
     * <p>This creates a JSON string conforming to the ResponseDTO format expected
     * by the Responses Connector. Unlike generateSifEnvelope(), this only includes
     * the three required fields: serviceName, serviceType, and payload.
     * 
     * @param serviceName the name of the service (e.g., "StudentPersonals")
     * @param serviceType the type of service (e.g., "OBJECT")
     * @param payload the message payload (JSON string)
     * @return JSON string representing a ResponseDTO
     */
    public String generateResponseDto(
        String serviceName,
        String serviceType,
        String payload
    ) {
        String json = String.format("""
            {
              "serviceName": "%s",
              "serviceType": "%s",
              "payload": %s
            }
            """,
            serviceName,
            serviceType,
            payload
        );
        
        log.debug("Generated ResponseDTO: serviceName={}, serviceType={}",
            serviceName, serviceType);
        
        return json;
    }
    
    /**
     * Generates a SIF message envelope.
     * 
     * <p>This wraps a payload in a SIF-compliant message envelope with
     * all required metadata fields.
     * 
     * @param messageId the message ID
     * @param messageType the message type (REQUEST, RESPONSE, EVENT)
     * @param serviceName the service name
     * @param serviceType the service type (OBJECT, UTILITY, FUNCTIONAL)
     * @param zoneId the zone ID
     * @param contextId the context ID
     * @param payload the message payload (JSON string)
     * @return JSON string representing a SIF message envelope
     */
    public String generateSifEnvelope(
        String messageId,
        String messageType,
        String serviceName,
        String serviceType,
        String zoneId,
        String contextId,
        String payload
    ) {
        String json = String.format("""
            {
              "messageId": "%s",
              "timestamp": "%s",
              "messageType": "%s",
              "serviceName": "%s",
              "serviceType": "%s",
              "zoneId": "%s",
              "contextId": "%s",
              "payload": %s
            }
            """,
            messageId,
            Instant.now().toString(),
            messageType,
            serviceName,
            serviceType,
            zoneId,
            contextId,
            payload
        );
        
        log.debug("Generated SIF envelope: messageId={}, messageType={}, serviceName={}",
            messageId, messageType, serviceName);
        
        return json;
    }
}
