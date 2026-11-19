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

package cafe.jeffrey.microscope.core.web.controllers.profile;

/**
 * Media types shared by the profile controllers.
 * <p>
 * Here rather than on whichever controller happened to declare one first: four controllers produce
 * protobuf, and three of them were importing the constant from the fourth, which reads as a
 * dependency between endpoints that have nothing to do with each other.
 */
public final class ProfileMediaTypes {

    /**
     * Flamegraphs are sent as protobuf rather than JSON: a graph is a deep tree of small nodes, and
     * the binary encoding is what keeps a large one inside a single response.
     */
    public static final String PROTOBUF = "application/x-protobuf";

    private ProfileMediaTypes() {
    }
}
