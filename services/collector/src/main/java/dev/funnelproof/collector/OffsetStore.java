package dev.funnelproof.collector;

import java.io.IOException;

/** A checkpoint advances only after its corresponding local read-model write succeeds. */
public interface OffsetStore {
    long load() throws IOException;

    void save(long offset) throws IOException;
}
