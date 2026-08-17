// SPDX-License-Identifier: MIT
package org.sifunity.broker.environment.dto;

import java.util.List;

/**
 * JSONB wrapper for the {@code code_items} column of the {@code code_sets} table.
 *
 * <p>Wraps the array of {@link CodeItemData} objects under a {@code codeItem} key,
 * matching the SIF 3.7 schema structure: {@code { "codeItem": [...] }}.</p>
 *
 * @param codeItem the list of code item entries stored in this code set
 * @see CodeItemData
 */
public record CodeItemsWrapper(
    List<CodeItemData> codeItem
) {}
