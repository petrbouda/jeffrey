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

package cafe.jeffrey.jib.payload;

/** Payloads that are not tied to a {@link ProvisionerSource}. */
public abstract class Payloads {

    private static final String PROFILER_BASE_NAME = "libasyncProfiler";
    private static final String PROFILER_EXTENSION = ".so";

    /**
     * async-profiler, carried by both payload flavours. It is the one payload that is genuinely
     * native whatever else the image carries, and at around 1 MB per architecture it is the cheap
     * half of the bundle.
     */
    public static final PayloadSpec PROFILER = new PayloadSpec.ArchScoped(
            PROFILER_BASE_NAME, PROFILER_EXTENSION, PayloadPermissions.READABLE);
}
