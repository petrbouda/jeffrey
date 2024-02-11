package pbouda.jeffrey.graph;

import one.FlameGraph;
import pbouda.jeffrey.repository.GraphContent;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

public class GraphExporterImpl implements GraphExporter {

    @Override
    public void export(Path targetPath, GraphContent content) {
        try (OutputStream os = Files.newOutputStream(targetPath);
             PrintStream out = new PrintStream(os, false, Charset.defaultCharset())) {
            new FlameGraph().dumpFromJson(content.content(), out);
        } catch (IOException e) {
            throw new RuntimeException("Cannot export flamegraph to a file: " + targetPath, e);
        }
    }
}
