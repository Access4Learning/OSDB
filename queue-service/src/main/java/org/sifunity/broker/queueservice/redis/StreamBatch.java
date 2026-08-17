// SPDX-License-Identifier: MIT
package org.sifunity.broker.queueservice.redis;

import java.util.List;

/**
 * Batch result from cursor-based stream iteration.
 *
 * @param storageKeys the storage keys extracted from the batch entries
 * @param entryIds    the stream entry IDs (needed for XDEL after processing)
 * @param lastId      the last entry ID in this batch (cursor for next call)
 * @param hasMore     true if there may be more entries after this batch
 */
public record StreamBatch(List<String> storageKeys, List<String> entryIds, String lastId, boolean hasMore) {}
