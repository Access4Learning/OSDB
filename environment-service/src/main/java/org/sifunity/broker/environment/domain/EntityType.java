// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.domain;

/**
 * Discriminator for the type of entity being tagged.
 * Used in the entity_tags table to identify which table entity_id references.
 */
public enum EntityType {
    SOLUTION,
    ZONE,
    CONTEXT,
    ENVIRONMENT,
    QUEUE
}
