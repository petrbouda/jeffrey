/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
module cafe.jeffrey.microscope.profile.advisor {
    requires transitive cafe.jeffrey.shared.common;
    requires transitive cafe.jeffrey.microscope.profile.ai.config;
    requires transitive cafe.jeffrey.microscope.profile.persistence.api;
    requires transitive cafe.jeffrey.microscope.profile.frame.ir;
    requires cafe.jeffrey.microscope.profile.flamegraph;
    requires cafe.jeffrey.microscope.profile.common;
    requires cafe.jeffrey.microscope.persistence.api;
    requires cafe.jeffrey.shared.persistence;
    requires transitive spring.ai.client.chat;
    requires spring.ai.model;
    requires spring.boot;
    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires tools.jackson.databind;
    requires java.sql;
    requires org.slf4j;

    exports cafe.jeffrey.profile.advisor.config;
    exports cafe.jeffrey.profile.advisor.fleet;
    exports cafe.jeffrey.profile.advisor.mcp;
    exports cafe.jeffrey.profile.advisor.prompt;
    exports cafe.jeffrey.profile.advisor.regression;
    exports cafe.jeffrey.profile.advisor.run;
    exports cafe.jeffrey.profile.advisor.settings;
    exports cafe.jeffrey.profile.advisor.source;
    exports cafe.jeffrey.profile.advisor.verify;

    opens cafe.jeffrey.profile.advisor.config to spring.core, spring.beans, spring.context;
    // The source tools are reflected over by Spring AI's @Tool scanner and by ReflectiveToolset.
    opens cafe.jeffrey.profile.advisor.source to spring.core, spring.ai.model;
}
