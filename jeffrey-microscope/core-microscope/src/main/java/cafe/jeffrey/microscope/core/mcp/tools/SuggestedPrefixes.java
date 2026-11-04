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

package cafe.jeffrey.microscope.core.mcp.tools;

import cafe.jeffrey.flamegraph.code.CodeAttribution.MethodValue;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Package prefixes that probably hold the application's own code, read off the methods a profile
 * spent its samples in: the busiest packages that are not the JDK or a well-known library.
 * <p>
 * A hint for a reader who has not named their packages yet, never a replacement for the ones they
 * read off their own source tree -- the answer that carries it says which it used.
 */
final class SuggestedPrefixes {

    /** How many packages a suggestion names. */
    static final int MAX_SUGGESTED = 3;

    /** A prefix keeps this many leading package segments: {@code com.acme.} rather than {@code com.}. */
    private static final int SEGMENTS = 2;

    private static final char PACKAGE_SEPARATOR = '.';

    /** Packages that are someone else's code in every application. */
    private static final Set<String> LIBRARY_PREFIXES = Set.of(
            "java.", "javax.", "jakarta.", "jdk.", "sun.", "com.sun.", "kotlin.", "kotlinx.", "scala.",
            "groovy.", "org.springframework.", "org.apache.", "io.netty.", "com.fasterxml.", "org.hibernate.",
            "ch.qos.", "org.slf4j.", "io.micrometer.", "reactor.", "org.eclipse.", "org.junit.", "com.zaxxer.",
            "org.postgresql.", "com.mysql.", "org.h2.", "org.hsqldb.", "io.grpc.", "com.google.", "io.opentelemetry.",
            "io.undertow.", "org.jboss.", "io.vertx.", "io.quarkus.", "io.micronaut.", "org.mybatis.", "org.jooq.",
            "org.glassfish.", "net.bytebuddy.", "org.aspectj.", "org.yaml.", "com.github.benmanes.",
            "io.lettuce.", "redis.clients.", "org.mongodb.", "com.mongodb.", "software.amazon.", "com.amazonaws.",
            "okhttp3.", "okio.", "retrofit2.", "feign.", "org.duckdb.", "org.openjdk.", "cafe.jeffrey.jfr.",
            "org.sqlite.", "org.xerial.", "tools.jackson.", "com.oracle.", "oracle.jdbc.", "org.mariadb.",
            "com.microsoft.", "org.flywaydb.", "org.liquibase.", "liquibase.", "org.reactivestreams.",
            "io.projectreactor.", "org.thymeleaf.", "org.objectweb.", "org.bouncycastle.");

    private SuggestedPrefixes() {
    }

    /**
     * The busiest non-library packages among these methods, by their inclusive cost.
     *
     * @param inclusiveByMethod every Java method's inclusive cost, as a code read returns it
     */
    static List<String> from(List<MethodValue> inclusiveByMethod) {
        Map<String, Long> byPackage = new LinkedHashMap<>();
        for (MethodValue value : inclusiveByMethod) {
            String className = value.method().className();
            if (className.isEmpty() || isLibrary(className)) {
                continue;
            }
            String prefix = prefixOf(className);
            if (prefix != null) {
                byPackage.merge(prefix, value.value(), Math::max);
            }
        }
        return byPackage.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(MAX_SUGGESTED)
                .map(Map.Entry::getKey)
                .toList();
    }

    private static boolean isLibrary(String className) {
        for (String prefix : LIBRARY_PREFIXES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** The first two package segments with their trailing dot, or null for a class in a shallower package. */
    private static String prefixOf(String className) {
        int end = -1;
        for (int i = 0; i < SEGMENTS; i++) {
            end = className.indexOf(PACKAGE_SEPARATOR, end + 1);
            if (end < 0) {
                return null;
            }
        }
        return className.substring(0, end + 1);
    }
}
