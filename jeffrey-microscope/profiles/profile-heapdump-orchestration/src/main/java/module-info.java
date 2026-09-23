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
module cafe.jeffrey.microscope.profile.heapdump.orchestration {
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.model;
    requires transitive cafe.jeffrey.microscope.profile.persistence.api;
    requires transitive cafe.jeffrey.microscope.profile.heapdump;
    requires transitive cafe.jeffrey.microscope.profile.heapdump.oql;
    requires cafe.jeffrey.microscope.profile.common;
    requires cafe.jeffrey.shared.storage.recording.api;
    requires tools.jackson.core;
    requires tools.jackson.databind;
    requires org.slf4j;

    exports cafe.jeffrey.profile.manager.additional;
    exports cafe.jeffrey.profile.manager.heapdump;
    exports cafe.jeffrey.profile.manager.heapdump.analysis;
}
