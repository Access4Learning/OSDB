// SPDX-License-Identifier: MIT
/**
 * Integration tests for the SIF Unity Broker system.
 * 
 * <p>This package contains comprehensive end-to-end tests that validate:
 * <ul>
 *   <li>All three message exchange patterns (IMMEDIATE, DELAYED, EVENT)</li>
 *   <li>SIF 3.7 compliance</li>
 *   <li>Authentication and authorization</li>
 *   <li>Error handling and resilience</li>
 *   <li>Message persistence and recovery</li>
 * </ul>
 * 
 * <p>Tests are organized by functional area:
 * <ul>
 *   <li>{@code pattern} - Message exchange pattern tests</li>
 *   <li>{@code compliance} - SIF 3.7 compliance tests</li>
 *   <li>{@code auth} - Authentication/authorization tests</li>
 *   <li>{@code error} - Error handling tests</li>
 *   <li>{@code persistence} - Message persistence tests</li>
 * </ul>
 * 
 * @since 1.0.0
 */
package org.sifunity.integration;
