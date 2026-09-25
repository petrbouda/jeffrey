/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.jfr.events.trace;

import cafe.jeffrey.jfr.events.trace.spi.SpanContextStorage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Chooses the {@link SpanContextStorage} the {@link Tracer} runs on: the highest-priority one the
 * running JVM can load.
 * <p>
 * An application normally carries one: {@code jeffrey-tracing-thread-local}, which the Spring Boot
 * starter brings, or {@code jeffrey-tracing-scoped-value}, added explicitly on Java 25. With both,
 * the ScopedValue one wins. A provider the JVM cannot load - the Java 25 class on an older JVM put on
 * the class path by mistake - is skipped rather than fatal.
 * <p>
 * Having none at all is fatal, and fails the Tracer's initialisation with a message naming both
 * artifacts: a Tracer with nowhere to keep the span in progress would record every span as a root
 * and every leaf event outside its trace, which is worse than a startup error.
 */
final class SpanContextStorages {

    private static final System.Logger LOG = System.getLogger(SpanContextStorages.class.getName());

    private static final String NONE_AVAILABLE = """
            No Jeffrey span-context storage on the class path. Add one of:
              cafe.jeffrey-analyst:jeffrey-tracing-scoped-value  (ScopedValue, Java 25+)
              cafe.jeffrey-analyst:jeffrey-tracing-thread-local  (ThreadLocal, Java 21+)""";

    private SpanContextStorages() {
    }

    static SpanContextStorage load() {
        return choose(loadable(ServiceLoader.load(SpanContextStorage.class, SpanContextStorage.class.getClassLoader())));
    }

    /**
     * @return the candidate with the highest priority
     * @throws IllegalStateException when there is none
     */
    static SpanContextStorage choose(List<SpanContextStorage> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");

        return candidates.stream()
                .max(Comparator.comparingInt(SpanContextStorage::priority))
                .orElseThrow(() -> new IllegalStateException(NONE_AVAILABLE));
    }

    /**
     * Instantiates every provider the JVM can load. A provider's class is loaded while the iterator
     * advances, so an unloadable one surfaces from {@code hasNext}/{@code next}: wrapped in a
     * {@link ServiceConfigurationError} on the class path, as the bare {@link LinkageError} on the
     * module path. Either way the iterator carries on with the next provider.
     */
    private static List<SpanContextStorage> loadable(ServiceLoader<SpanContextStorage> loader) {
        List<SpanContextStorage> loaded = new ArrayList<>();
        Iterator<SpanContextStorage> providers = loader.iterator();
        while (true) {
            try {
                if (!providers.hasNext()) {
                    return loaded;
                }
                loaded.add(providers.next());
            } catch (ServiceConfigurationError | LinkageError e) {
                LOG.log(System.Logger.Level.DEBUG,
                        "Span-context storage not loadable on this JVM, skipped: reason={0}", e.toString());
            }
        }
    }
}
