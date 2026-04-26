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

module pbouda.jeffrey.local.core {
    requires transitive pbouda.jeffrey.shared.common;
    requires transitive pbouda.jeffrey.shared.persistence;
    requires transitive pbouda.jeffrey.local.persistence.api;
    requires transitive pbouda.jeffrey.local.sql.persistence;
    requires transitive pbouda.jeffrey.profile.management;
    requires transitive pbouda.jeffrey.recording.storage.api;
    requires transitive pbouda.jeffrey.recording.storage.filesystem;
    requires transitive pbouda.jeffrey.local.grpc.client;
    requires transitive pbouda.jeffrey.server.api;
    requires transitive io.grpc;
    requires transitive io.grpc.stub;
    requires typesafe.config;
    requires net.bytebuddy;
    requires jeffrey.events;
    requires jakarta.annotation;
    requires org.apache.tomcat.embed.core;
    requires jdk.jfr;
    requires java.sql;
    requires java.net.http;
    requires spring.beans;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.core;
    requires spring.web;
    requires spring.webmvc;
    requires spring.boot.tomcat;
    requires spring.boot.servlet;
    requires spring.boot.web.server;
    requires spring.boot.webmvc;
    requires com.fasterxml.jackson.annotation;
    requires tools.jackson.databind;
    requires org.slf4j;

    exports pbouda.jeffrey.local.core;
    exports pbouda.jeffrey.local.core.configuration;
    exports pbouda.jeffrey.local.core.initializer;
    exports pbouda.jeffrey.local.core.manager;
    exports pbouda.jeffrey.local.core.manager.download;
    exports pbouda.jeffrey.local.core.manager.project;
    exports pbouda.jeffrey.local.core.manager.qanalysis;
    exports pbouda.jeffrey.local.core.manager.workspace;
    exports pbouda.jeffrey.local.core.persistence;
    exports pbouda.jeffrey.local.core.recording;
    exports pbouda.jeffrey.local.core.resources.pub;
    exports pbouda.jeffrey.local.core.resources.request;
    exports pbouda.jeffrey.local.core.resources.response;
    exports pbouda.jeffrey.local.core.resources.workspace;
    exports pbouda.jeffrey.local.core.web;
    exports pbouda.jeffrey.local.core.web.controllers;
    exports pbouda.jeffrey.local.core.web.controllers.profile;
}
