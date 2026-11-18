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
module cafe.jeffrey.microscope.profile.common {
    requires transitive cafe.jeffrey.microscope.common;
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.model;
    requires cafe.jeffrey.microscope.notification;
    requires transitive tools.jackson.databind;
    requires org.slf4j;
    requires cafe.jeffrey.jfr.events;

    exports cafe.jeffrey.microscope.profile.common.analysis;
    exports cafe.jeffrey.microscope.profile.common.config;
    exports cafe.jeffrey.microscope.profile.common.event;
    exports cafe.jeffrey.microscope.profile.common.exceptions;
    exports cafe.jeffrey.microscope.profile.common.model;
    exports cafe.jeffrey.microscope.profile.common.pipeline;
    exports cafe.jeffrey.microscope.profile.common.operation;
    exports cafe.jeffrey.microscope.profile.common.treetable;
    // The JFR model types (frames, stack traces, threads, methods) a parsed recording is read as
    exports cafe.jeffrey.microscope.profile.common.jfr;
}
