package pbouda.jeffrey.repository;

import java.util.List;

public interface FlamegraphRepository {

    /**
     * Retrieve all flamegraphs that are available in the given implementation of repository.
     *
     * @param extension include only files with this extension
     * @return a collection of all flamegraphs.
     */
    List<FlamegraphFile> list(String extension);

    String content(String filename);
}
