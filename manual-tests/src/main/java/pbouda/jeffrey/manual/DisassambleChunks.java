package pbouda.jeffrey.manual;

import pbouda.jeffrey.profile.parser.chunk.JfrParser;
import pbouda.jeffrey.tools.impl.jdk.JdkJfrTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DisassambleChunks {

    static void main() throws IOException {
        Path resolve = Files.createDirectories(Path.of("manual-tests/jfrs").resolve("chunks-1"));
        Path resolve1 = Files.createDirectories(Path.of("manual-tests/jfrs").resolve("chunks-2"));

        JfrParser.disassemble(Path.of("manual-tests/jfrs/profile.jfr"), resolve);

        var jfrTool = new JdkJfrTool();
        jfrTool.initialize();
        jfrTool.disassemble(Path.of("manual-tests/jfrs/profile.jfr"), resolve1);
    }
}
