/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.model.repository.matcher;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Recognises a JVM unified-logging file by the {@code .jvm-log} extension — Jeffrey's own convention,
 * chosen so the file does not end in {@code .log} and is never mistaken for an application log. The JVM
 * rotates by appending a number ({@code gc.jvm-log.0}, {@code gc.jvm-log.1}) and never compresses.
 */
public class JvmLogFileMatcher implements Predicate<String> {

    private static final Pattern JVM_LOG_PATTERN = Pattern.compile(".*\\.jvm-log(\\.[0-9]+)?$");

    @Override
    public boolean test(String filename) {
        if (filename == null) {
            return false;
        }
        return JVM_LOG_PATTERN.matcher(filename).matches();
    }
}
