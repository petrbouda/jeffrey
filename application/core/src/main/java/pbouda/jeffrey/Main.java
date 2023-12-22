package pbouda.jeffrey;

import one.*;
import one.jfr.JfrReader;

import java.nio.file.Path;

public class Main {
    
    public void main() throws Exception {
        Path desktopPath = Path.of("/home/pbouda/Desktop");
        Arguments args = ArgumentsBuilder.create()
                .withInput(desktopPath.resolve("dump.jfr"))
                .withOutput(desktopPath.resolve("my-profile.html"))
                .withTitle("")
                .build();

        FlameGraph fg = new FlameGraph(args);

        try (JfrReader jfr = new JfrReader(desktopPath.resolve("dump.jfr").toString())) {
            new jfr2flame(jfr, args).convert(fg);
        }

        fg.dump();
    }
}
