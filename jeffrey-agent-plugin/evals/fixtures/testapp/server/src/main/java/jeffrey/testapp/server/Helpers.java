package jeffrey.testapp.server;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public abstract class Helpers {

    public static Collection<Integer> generateIds(int max, int count) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return Stream.generate(() -> random.nextInt(max))
                .limit(count)
                .toList();
    }

    public static int generateId(int max) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return random.nextInt(max);
    }
}
