package pbouda.jeffrey.graph;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;

public interface GraphExporter {

    void export(Path targetPath, JsonNode data);

}
