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

package cafe.jeffrey.microscope.core.manager.recordings.builder;

import cafe.jeffrey.microscope.model.ProfileKind;
import cafe.jeffrey.microscope.model.RecordingEventSource;

import java.util.Map;

/**
 * The builder for every initializable kind: the one place that says which kind is built how.
 *
 * @param builders one builder per kind
 */
public record ProfileBuilders(Map<ProfileKind, ProfileBuilder> builders) {

    public ProfileBuilders {
        builders = Map.copyOf(builders);
    }

    /**
     * @throws IllegalArgumentException when no builder exists for the source's kind
     */
    public ProfileBuilder of(RecordingEventSource source) {
        ProfileBuilder builder = builders.get(source.kind());
        if (builder == null) {
            throw new IllegalArgumentException("A recording of this kind cannot be initialized: " + source);
        }
        return builder;
    }
}
