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

import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;

import java.util.Map;

/**
 * The outcome of installing a {@link PayloadPlan}: the image layer to add, and the environment
 * defaults that tell the entrypoint what was installed.
 *
 * <p>There is always a layer. Every image carries a provisioner, so a plan never installs nothing.
 *
 * @param environment the {@code JEFFREY_*} defaults, which an explicit {@code profilerPath} still
 *                    overrides
 */
public record PayloadInstallation(FileEntriesLayer layer, Map<String, String> environment) {

    public PayloadInstallation {
        if (layer == null) {
            throw new IllegalArgumentException("Payload layer must not be null");
        }
        environment = Map.copyOf(environment);
    }
}
