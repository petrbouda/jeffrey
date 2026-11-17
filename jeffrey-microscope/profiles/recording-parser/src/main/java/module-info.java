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

/**
 * Every recording format Microscope reads. The vendored protobuf classes
 * ({@code com.google.perftools.profiles.*}, {@code io.opentelemetry.proto.*}) stay module-internal:
 * only the parser packages are exported.
 */
module cafe.jeffrey.microscope.profile.parser {
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.model;
    requires transitive cafe.jeffrey.microscope.profile.common;
    requires transitive cafe.jeffrey.microscope.profile.persistence;
    requires cafe.jeffrey.shared.notifications;
    requires cafe.jeffrey.jfr.events;
    requires com.google.protobuf;
    requires tools.jackson.databind;
    requires jdk.jfr;
    requires org.slf4j;

    exports cafe.jeffrey.jfrparser.jdk;
    exports cafe.jeffrey.jfrparser.raw;
    exports cafe.jeffrey.pprofparser;
    exports cafe.jeffrey.otlpparser;
}
