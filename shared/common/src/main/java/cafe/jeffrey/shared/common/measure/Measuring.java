package cafe.jeffrey.shared.common.measure;

import java.time.Duration;
import java.util.function.Supplier;

public abstract class Measuring {

    public static Duration r(Runnable runnable) {
        long start = System.nanoTime();
        runnable.run();
        return Duration.ofNanos(System.nanoTime() - start);
    }

    public static <T> Elapsed<T> s(Supplier<T> supplier) {
        long start = System.nanoTime();
        T entity = supplier.get();
        Duration duration = Duration.ofNanos(System.nanoTime() - start);
        return new Elapsed<>(duration, entity);
    }
}
