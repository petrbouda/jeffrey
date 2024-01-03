package pbouda.jeffrey.repository;

import java.util.List;

public interface FlamegraphRepository {

    /**
     * Retrieve all flamegraphs that are available in the given implementation of repository.
     *
     * @return a collection of all flamegraphs.
     */
    List<FlamegraphFile> list();

    String content(String filename);
}
