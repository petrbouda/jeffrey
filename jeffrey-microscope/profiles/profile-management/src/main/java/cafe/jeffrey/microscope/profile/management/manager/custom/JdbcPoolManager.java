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

package cafe.jeffrey.microscope.profile.management.manager.custom;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.profile.management.manager.custom.model.jdbc.pool.JdbcPoolData;
import cafe.jeffrey.microscope.profile.management.timeseries.SingleSerie;

import java.util.List;
import java.util.function.Function;

public interface JdbcPoolManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, JdbcPoolManager> {
    }

    List<JdbcPoolData> allPoolsData();

    SingleSerie timeseries(String poolName, Type eventType);
}
