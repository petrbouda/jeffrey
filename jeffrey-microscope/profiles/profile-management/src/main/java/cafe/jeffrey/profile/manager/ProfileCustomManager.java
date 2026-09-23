/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.custom.ExchangeDirection;
import cafe.jeffrey.profile.manager.custom.GrpcManager;
import cafe.jeffrey.profile.manager.custom.HttpManager;
import cafe.jeffrey.profile.manager.custom.JdbcPoolManager;
import cafe.jeffrey.profile.manager.custom.JdbcStatementManager;
import cafe.jeffrey.profile.manager.custom.MethodTracingManager;

import java.util.function.Function;

public interface ProfileCustomManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileManager, ProfileCustomManager> {
    }

    ProfileManager parent();

    JdbcPoolManager jdbcPoolManager();

    JdbcStatementManager jdbcStatementManager();

    /**
     * The HTTP dashboard for one direction. There is no direction-less accessor on purpose: the
     * server and client halves answer different questions, and a caller that did not choose would
     * silently get one of them.
     */
    HttpManager httpManager(ExchangeDirection direction);

    GrpcManager grpcManager(ExchangeDirection direction);

    MethodTracingManager methodTracingManager();
}
