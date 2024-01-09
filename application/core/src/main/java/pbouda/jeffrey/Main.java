package pbouda.jeffrey;

import one.*;
import one.jfr.JfrReader;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.flightrecorder.jdk.JdkTypeIDs;

import java.nio.file.Path;
import java.time.Instant;

public class Main {


    
    public void main() throws Exception {
        System.out.println(Integer.MAX_VALUE / 60 / 60 / 24);

//        Instant start = Instant.parse("2024-01-09T21:41:56.6Z");
//        Instant end = Instant.parse("2024-01-09T21:41:57.8Z");
//
//        System.out.println(end.minusMillis(start.toEpochMilli()).getEpochSecond());

//        Path desktopPath = Path.of("/home/pbouda/Desktop");
//        Arguments args = ArgumentsBuilder.create()
//                .withInput(desktopPath.resolve("dump.jfr"))
//                .withOutput(desktopPath.resolve("my-profile.html"))
//                .withTitle("")
//                .build();
//
//        FlameGraph fg = new FlameGraph(args);
//
//        try (JfrReader jfr = new JfrReader(desktopPath.resolve("dump.jfr").toString())) {
//            new jfr2flame(jfr, args).convert(fg);
//        }
//
//     convert   fg.dump();
    }
}
