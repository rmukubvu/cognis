package io.cognis.core.usage;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Persistence contract for {@link UsageRecord} entries.
 * Implementations must be thread-safe.
 */
public interface UsageStore {
    /** Appends a single usage record to the store. */
    void append(UsageRecord record) throws IOException;

    /** Returns all records with a timestamp on or after {@code since}. */
    List<UsageRecord> findSince(Instant since) throws IOException;

    /** Returns every record in the store, in insertion order. */
    List<UsageRecord> findAll() throws IOException;
}
