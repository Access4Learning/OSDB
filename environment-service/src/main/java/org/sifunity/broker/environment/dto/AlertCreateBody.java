// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.sifunity.broker.common.constant.AlertLevel;
import org.sifunity.broker.common.constant.Exchange;

/**
 * Request body for creating an alert.
 * 
 * <p>Contains required fields ({@code reporter}, {@code exchange}, {@code level})
 * and optional descriptive fields per the SIF 3.7 alert schema.</p>
 * 
 * @param reporter             application name identifying the reporter (required)
 * @param exchange             message exchange classification (required)
 * @param level                alert severity level (required)
 * @param cause                application or component that caused the condition
 * @param description          human-readable description
 * @param messageID            originating message ID for context correlation
 * @param body                 message body content
 * @param error                error details
 * @param xpath                XPath to the element that caused the alert
 * @param category             SIF error category code
 * @param code                 SIF error code
 * @param internal             internal application-specific error identifier
 * @param SIF_ExtendedElements caller-provided extended elements
 */
public record AlertCreateBody(
    @NotBlank(message = "Reporter is required")
    String reporter,

    @NotNull(message = "Exchange is required")
    Exchange exchange,

    @NotNull(message = "Level is required")
    AlertLevel level,

    String cause,
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
