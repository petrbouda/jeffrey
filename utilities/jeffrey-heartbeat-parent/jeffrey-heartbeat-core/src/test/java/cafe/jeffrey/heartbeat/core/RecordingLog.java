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
package cafe.jeffrey.heartbeat.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A {@link HeartbeatLog} that keeps what it was told, so a test can assert on it. */
final class RecordingLog implements HeartbeatLog {

    static final HeartbeatLog SILENT = new RecordingLog();

    final List<String> warnings = new CopyOnWriteArrayList<>();

    @Override
    public void info(String message) {
    }

    @Override
    public void warn(String message, Throwable error) {
        warnings.add(message);
    }

    @Override
    public void debug(String message, Throwable error) {
    }

    List<String> warnings() {
        return List.copyOf(warnings);
    }
}
