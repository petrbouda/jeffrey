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
package cafe.jeffrey.agent;

import cafe.jeffrey.heartbeat.core.HeartbeatLog;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

/**
 * Routes the heartbeat core's log lines into {@link System.Logger}: the agent may bring no logging
 * library onto the application's class path, and must not initialize the application's own before
 * its {@code main} has configured it.
 */
final class SystemLoggerHeartbeatLog implements HeartbeatLog {

    private final Logger logger;

    SystemLoggerHeartbeatLog(Class<?> owner) {
        this.logger = System.getLogger(owner.getName());
    }

    @Override
    public void info(String message) {
        logger.log(Level.INFO, message);
    }

    @Override
    public void warn(String message, Throwable error) {
        logger.log(Level.WARNING, message, error);
    }

    @Override
    public void debug(String message, Throwable error) {
        logger.log(Level.DEBUG, message, error);
    }
}
