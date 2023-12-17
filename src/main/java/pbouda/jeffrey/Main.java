package pbouda.jeffrey;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
//        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 9000), 0);
//        server.createContext("/", FileHan)

        System.out.println("Hello world!");

        Path profilePath = Path.of("/home/pbouda/Desktop/dump.jfr");
//        IItemCollection events = JfrLoaderToolkit
//                .loadEvents()
//                .loadEvents(profilePath.toFile());

        System.out.println();

//        for (IRule rule : RuleRegistry.getRules()) {
//            RunnableFuture<Result> future = rule.evaluate(events, IPreferenceValueProvider.DEFAULT_VALUES);
//            future.run();
//            Result result = future.get();
//            if (result.getScore() > 50) {
//                System.out.println(String.format("[Score: %3.0f] Rule ID: %s, Rule name: %s, Short description: %s",
//                        result.getScore(), result.getRule().getId(), result.getRule().getName(),
//                        result.getShortDescription()));
//            }
//        }

        for (int i = 50; i >= 0; i--) {
            System.out.println(STR."\{i * 20},");
        }
    }
}
