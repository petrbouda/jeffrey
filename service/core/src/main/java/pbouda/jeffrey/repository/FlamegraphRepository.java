package pbouda.jeffrey.repository;

import java.util.List;

public interface FlamegraphRepository {

    /**
     * Retrieve all flamegraphs that are available in the given implementation of repository.
     *
     * @param profile profile that owns the flamegraphs.
     * @return a collection of all flamegraphs.
     */
    List<FlamegraphFile> list(String profile);

    String content(String filename);
}
