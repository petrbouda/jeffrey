package pbouda.jeffrey.graph;

import pbouda.jeffrey.repository.model.GraphContent;

import java.nio.file.Path;

public interface GraphExporter {

    void export(Path targetPath, GraphContent data);

}
