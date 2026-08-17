// SPDX-License-Identifier: MIT
/**
 * SIF Broker Common Library
 * 
 * <p>This package provides common contracts, data models, exceptions, and utilities
 * for all SIF Unity Broker microservices. It ensures consistency in data structures,
 * error handling, and SIF 3.7 compliance across the entire broker implementation.</p>
 * 
 * <h2>Main Components</h2>
 * <ul>
 *   <li><b>dto</b> - Data Transfer Objects for shared domain entities</li>
 *   <li><b>model</b> - Domain models including SIF message envelopes</li>
 *   <li><b>exception</b> - Exception hierarchy with HTTP status code mapping</li>
 *   <li><b>constant</b> - Constants and enumerations</li>
 *   <li><b>util</b> - Utility classes for SIF compliance, validation, and authentication</li>
 *   <li><b>config</b> - Configuration classes for Jackson and other frameworks</li>
 * </ul>
 * 
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Immutable DTOs with validation annotations</li>
 *   <li>SIF 3.7 compliant message envelopes and error responses</li>
 *   <li>Standardized exception handling</li>
 *   <li>HMAC-SHA256 authentication utilities</li>
 *   <li>Structured logging with correlation IDs</li>
 *   <li>Test utilities and generators for property-based testing</li>
 * </ul>
 * 
 * @since 1.0.0
 * @version 1.0.0-SNAPSHOT
 */
package org.sifunity.broker.common;
