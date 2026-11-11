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

package cafe.jeffrey.hub.core;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

import java.util.List;
import java.util.stream.Stream;

/**
 * What the hub's native image needs that Spring's AOT pass cannot see for itself. On the JVM these
 * hints are never read.
 *
 * <ul>
 *     <li>the resources read by name: the release tag behind {@code --version} and the scheduler's
 *     built-in defaults;</li>
 *     <li>lz4-java's pure-Java codecs. {@code LZ4Factory} and {@code XXHashFactory} assemble each
 *     codec's class name from strings and read its {@code INSTANCE} field by reflection. The JNI
 *     codec is left out: its {@code .so} is not in the image, so the factories fall back to the
 *     Java ones registered here.</li>
 * </ul>
 */
public final class HubRuntimeHints implements RuntimeHintsRegistrar {

    private static final List<String> RESOURCES = List.of(
            "jeffrey-tag.txt",
            "scheduler-defaults.properties");

    /** The two pure-Java flavours the lz4-java factories fall back to without the JNI library. */
    private static final List<String> JAVA_IMPLEMENTATIONS = List.of("JavaSafe", "JavaUnsafe");

    private static final String LZ4_PACKAGE = "net.jpountz.lz4.";
    private static final String XXHASH_PACKAGE = "net.jpountz.xxhash.";
    private static final String STREAMING_FACTORY_SUFFIX = "$Factory";

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        RESOURCES.forEach(resource -> hints.resources().registerPattern(resource));

        JAVA_IMPLEMENTATIONS.stream()
                .flatMap(HubRuntimeHints::codecClasses)
                .map(TypeReference::of)
                .forEach(type -> hints.reflection().registerType(type, MemberCategory.ACCESS_PUBLIC_FIELDS));
    }

    private static Stream<String> codecClasses(String implementation) {
        return Stream.of(
                LZ4_PACKAGE + "LZ4" + implementation + "Compressor",
                LZ4_PACKAGE + "LZ4HC" + implementation + "Compressor",
                LZ4_PACKAGE + "LZ4" + implementation + "FastDecompressor",
                LZ4_PACKAGE + "LZ4" + implementation + "SafeDecompressor",
                XXHASH_PACKAGE + "XXHash32" + implementation,
                XXHASH_PACKAGE + "XXHash64" + implementation,
                XXHASH_PACKAGE + "StreamingXXHash32" + implementation + STREAMING_FACTORY_SUFFIX,
                XXHASH_PACKAGE + "StreamingXXHash64" + implementation + STREAMING_FACTORY_SUFFIX);
    }
}
