package pbouda.jeffrey;

import one.*;
import one.jfr.JfrReader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    
    public void main() throws Exception {
        Path desktopPath = Path.of("/Users/petrbouda/Desktop");
        Path collapsed = desktopPath.resolve("my-profile.html");
//        Files.createFile(collapsed);

        Arguments args = ArgumentsBuilder.create()
                .withInput(desktopPath.resolve("das.jfr"))
                .withOutput(collapsed)
                .withTitle("")
                .build();

        FlameGraph fg = new FlameGraph(args);

//        CollapsedStacks fg = new CollapsedStacks(args);
        try (JfrReader jfr = new JfrReader(desktopPath.resolve("das.jfr").toString())) {
            new jfr2flame(jfr, args).convert(fg);
        }

        fg.dump();
    }
}
