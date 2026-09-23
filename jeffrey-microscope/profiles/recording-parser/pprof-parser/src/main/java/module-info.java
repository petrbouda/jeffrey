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
module cafe.jeffrey.microscope.profile.parser.pprof {
    requires transitive cafe.jeffrey.microscope.profile.persistence.api;
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.model;
    requires cafe.jeffrey.microscope.profile.common;
    requires com.google.protobuf;
    requires tools.jackson.databind;
    requires org.slf4j;

    // The vendored pprof protobuf classes (com.google.perftools.profiles.*) stay module-internal —
    // only the parser entry points are part of the module's API.
    exports cafe.jeffrey.pprofparser;
}
