package pbouda.jeffrey;

import java.util.stream.IntStream;

public class Main {


    public void main() throws Exception {
        int[] range = IntStream.range(0, 1).toArray();
        System.out.println();

//        Instant start = Instant.parse("2024-01-09T21:41:56.6Z");
//        Instant end = Instant.parse("2024-01-09T21:41:57.8Z");
//
//        Instant relative = end.minusMillis(start.toEpochMilli());
//        System.out.println(relative.getEpochSecond());
//        System.out.println(relative.get(ChronoField.MILLI_OF_SECOND));

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
