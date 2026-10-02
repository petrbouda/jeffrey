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
package cafe.jeffrey.heartbeat;

import cafe.jeffrey.heartbeat.core.HeartbeatLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes the core's log lines into SLF4J, so the application's logging configuration decides. */
final class Slf4jHeartbeatLog implements HeartbeatLog {

    private final Logger logger;

    Slf4jHeartbeatLog(Class<?> owner) {
        this.logger = LoggerFactory.getLogger(owner);
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message, Throwable error) {
        logger.warn(message, error);
    }

    @Override
    public void debug(String message, Throwable error) {
        logger.debug(message, error);
    }
}
