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
 * Where the async-profiler agent a session runs with came from. Reported in the provisioner's
 * verdict log line; it is not written into any marker, since nothing reads it.
 */
public enum ProfilerSource {

    /** {@code profiler-command} names its own library with {@code -agentpath:<library>=...}. */
    AGENT_PATH,

    /** {@code profiler-command} carries options only, run on the {@code profiler-path} library. */
    CONFIGURED_OPTIONS,

    /** No {@code profiler-command}: the {@code profiler-path} library with the built-in options. */
    BUILT_IN,

    /** The {@code profiler-path} library was needed but is not there, so the session runs unprofiled. */
    DISABLED
}
