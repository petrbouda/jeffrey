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
module cafe.jeffrey.microscope.profile.threads {
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.model;
    requires transitive cafe.jeffrey.microscope.profile.persistence.api;
    requires transitive cafe.jeffrey.microscope.profile.timeseries;
    requires cafe.jeffrey.microscope.profile.parser.api;
    requires org.eclipse.collections.api;
    requires org.eclipse.collections.impl;
    requires tools.jackson.databind;
    requires org.slf4j;

    exports cafe.jeffrey.profile.thread;
    exports cafe.jeffrey.profile.manager.thread;
    exports cafe.jeffrey.profile.manager.thread.builder;
    exports cafe.jeffrey.profile.manager.model.thread;
    exports cafe.jeffrey.profile.manager.model.thread.dump;
    exports cafe.jeffrey.profile.manager.model.virtualthread;
}
