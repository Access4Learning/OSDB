// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.Exchange;

import java.util.UUID;

/**
 * Data Transfer Object for alert information in API responses.
 * 
 * <p>Contains all alert fields including the broker-assigned {@code id}
 * and server-populated {@code SIF_ExtendedElements}.</p>
 * 
 * @param id                   broker-assigned UUID
 * @param reporter             application name identifying the reporter
 * @param cause                application or component that caused the condition
 * @param exchange             message exchange classification
 * @param level                alert severity level
 * @param description          human-readable description
 * @param messageID            originating message ID
 * @param body                 message body content
 * @param error                error details
 * @param xpath                XPath to the element that caused the alert
 * @param category             SIF error category code
 * @param code                 SIF error code
 * @param internal             internal application-specific error identifier
 * @param SIF_ExtendedElements merged extended elements (server + caller)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlertDTO(
    UUID id,
    String reporter,
    String cause,
    Exchange exchange,
    AlertLevel level,
    String description,
    String messageID,
    String body,
    String error,
    String xpath,
    Integer category,
    Integer code,
    String internal,
    SifExtendedElements SIF_ExtendedElements
) {}
