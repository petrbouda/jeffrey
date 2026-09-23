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

package cafe.jeffrey.provisioner;

/**
 * Which source the provisioner's profiler command was resolved from. Reported in the
 * provisioner's verdict log line; it is not written into any marker, since nothing reads it.
 */
public enum ProfilerSettingsSource {

    /**
     * Explicit {@code profiler-command}, from the provisioner's HOCON config or the
     * {@code JEFFREY_PROFILER_COMMAND} environment variable.
     */
    CONFIGURED,

    /** Built-in provisioner default ({@code CliConstants.DEFAULT_PROFILER_CONFIG}) */
    BUILT_IN
}
