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

/**
 * Where the heartbeat loop reports what happened. An interface rather than a logging library,
 * because this module may depend on nothing: each caller adapts it to the logging it already has.
 */
public interface HeartbeatLog {

    void info(String message);

    void warn(String message, Throwable error);

    void debug(String message, Throwable error);
}
