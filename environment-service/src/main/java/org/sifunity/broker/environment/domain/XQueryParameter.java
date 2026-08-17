// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

/**
 * Value object representing a parameter definition for a Named Query (xQueryTemplate).
 *
 * <p>Parameters describe the inputs that consumers must (or may) supply when
 * invoking a named query. This record is serialized as JSONB within the
 * {@link XQueryTemplate#getParameters()} column.</p>
 *
 * @param name         the parameter name (used as the query-string key)
 * @param type         the SIF data type of the parameter (e.g., "xs:string", "xs:int")
 * @param defaultValue the default value if the consumer does not supply one (nullable)
 * @param description  human-readable description of the parameter (nullable)
 * @param required     whether the parameter is mandatory for invocation (nullable, defaults false)
 *
 * @see XQueryTemplate
 */
public record XQueryParameter(
    String name,
    String type,
    String defaultValue,
    String description,
    Boolean required
) {}
