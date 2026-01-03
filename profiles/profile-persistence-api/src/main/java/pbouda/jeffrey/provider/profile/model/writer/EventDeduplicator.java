package pbouda.jeffrey.provider.profile.model.writer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class EventDeduplicator {

    private final ConcurrentMap<Long, Boolean> frameUsed = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Boolean> stacktraceUsed = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Boolean> threadUsed = new ConcurrentHashMap<>();

    public Boolean checkAndAddFrame(long value) {
        return checkAndAdd(value, frameUsed);
    }

    public Boolean checkAndAddStacktrace(long value) {
        return checkAndAdd(value, stacktraceUsed);
    }

    public Boolean checkAndAddThread(long value) {
        return checkAndAdd(value, threadUsed);
    }

    /**
     * Checks if the value was already added. If not, it adds it and returns true.
     *
     * @param value deduplicated value
     * @return true if the value was not present and was added, false if it was already present.
     */
    private static Boolean checkAndAdd(long value, ConcurrentMap<Long, Boolean> used) {
        return used.putIfAbsent(value, Boolean.TRUE) == null;
    }
}
