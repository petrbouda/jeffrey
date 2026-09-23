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

package cafe.jeffrey.microscope.model.repository.matcher;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Recognises an application log as any {@code .log} file, in every shape a rolling appender leaves behind.
 * The JVM's own logs stay out of the way by not ending in {@code .log}: unified logging is written to
 * {@code *.jvm-log} ({@link JvmLogFileMatcher}). The crash log does end in {@code .log} — under Jeffrey's
 * {@code hs-jvm-err.log} and the JVM's default {@code hs_err_pid<pid>.log} alike — and is claimed by
 * {@link HsJvmErrorLogFileMatcher} on the type declared before this one.
 *
 * <p>Two rotation shapes, because Logback and Log4j2 put the {@code %d}/{@code %i} token on either side of
 * the extension: before it ({@code service.2026-09-13.log}, {@code service-2026-09-13.1.log}) needs no
 * rule at all, and after it ({@code service.log.1}, {@code service.log.2026-09-13}) is a run of digit-led
 * tokens, so {@code service.log.tar} is not read as a roll-over.
 *
 * <p>The compression suffix list is closed on purpose: it is what the rolling appenders and logrotate
 * actually emit, and an open tail would make any {@code .log.<anything>} an application log.
 */
public class AppLogFileMatcher implements Predicate<String> {

    private static final String EXTENSION = "\\.log";
    private static final String ROTATION_AFTER_EXTENSION = "(?:\\.[0-9][^.]*)*";
    private static final String COMPRESSION_SUFFIX = "(?:\\.(?:gz|zip|zst|xz|bz2|lz4))?";

    private static final Pattern APP_LOG_PATTERN = Pattern.compile(
            ".*" + EXTENSION + ROTATION_AFTER_EXTENSION + COMPRESSION_SUFFIX + "$");

    @Override
    public boolean test(String filename) {
        if (filename == null) {
            return false;
        }
        return APP_LOG_PATTERN.matcher(filename).matches();
    }
}
