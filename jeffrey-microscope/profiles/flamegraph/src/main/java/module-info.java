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
// com.google.protobuf is an automatic module (named only by its jar manifest), so javac warns that
// re-exporting it is fragile. Re-exported deliberately: the generated flamegraph messages are the
// exported API, and every caller that builds or parses one reads protobuf types directly.
@SuppressWarnings("requires-transitive-automatic")
module cafe.jeffrey.microscope.profile.flamegraph {
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.model;
    requires transitive cafe.jeffrey.microscope.profile.frame.ir;
    requires transitive cafe.jeffrey.microscope.profile.timeseries;
    requires transitive cafe.jeffrey.microscope.profile.persistence.api;
    requires transitive com.google.protobuf;
    requires tools.jackson.databind;
    requires org.slf4j;
    requires cafe.jeffrey.jfr.events;

    exports cafe.jeffrey.flamegraph;
    exports cafe.jeffrey.flamegraph.export;
    exports cafe.jeffrey.flamegraph.api;
    exports cafe.jeffrey.flamegraph.diff;
    exports cafe.jeffrey.flamegraph.provider;
}
