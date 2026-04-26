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

module pbouda.jeffrey.server.core {
    requires transitive pbouda.jeffrey.shared.common;
    requires transitive pbouda.jeffrey.shared.persistence;
    requires transitive pbouda.jeffrey.shared.persistentqueue;
    requires transitive pbouda.jeffrey.shared.folderqueue;
    requires transitive pbouda.jeffrey.server.persistence.api;
    requires transitive pbouda.jeffrey.server.sql.persistence;
    requires transitive pbouda.jeffrey.server.api;
    requires transitive io.grpc;
    requires transitive io.grpc.stub;
    requires transitive io.grpc.netty.shaded;
    requires io.grpc.services;
    requires com.google.protobuf;
    requires com.google.common;
    requires net.bytebuddy;
    requires jeffrey.events;
    requires org.apache.tomcat.embed.core;
    requires java.sql;
    requires java.net.http;
    requires jdk.jfr;
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

    exports pbouda.jeffrey.server.core;
    exports pbouda.jeffrey.server.core.appinitializer;
    exports pbouda.jeffrey.server.core.configuration;
    exports pbouda.jeffrey.server.core.configuration.properties;
    exports pbouda.jeffrey.server.core.configuration.workspace;
    exports pbouda.jeffrey.server.core.grpc;
    exports pbouda.jeffrey.server.core.jfr;
    exports pbouda.jeffrey.server.core.manager;
    exports pbouda.jeffrey.server.core.manager.jfr;
    exports pbouda.jeffrey.server.core.manager.project;
    exports pbouda.jeffrey.server.core.manager.workspace;
    exports pbouda.jeffrey.server.core.project;
    exports pbouda.jeffrey.server.core.project.pipeline;
    exports pbouda.jeffrey.server.core.project.repository;
    exports pbouda.jeffrey.server.core.project.repository.file;
    exports pbouda.jeffrey.server.core.project.template;
    exports pbouda.jeffrey.server.core.repository;
    exports pbouda.jeffrey.server.core.resources.response;
    exports pbouda.jeffrey.server.core.resources.workspace;
    exports pbouda.jeffrey.server.core.scheduler;
    exports pbouda.jeffrey.server.core.scheduler.job;
    exports pbouda.jeffrey.server.core.scheduler.job.descriptor;
    exports pbouda.jeffrey.server.core.scheduler.model;
    exports pbouda.jeffrey.server.core.streaming;
    exports pbouda.jeffrey.server.core.web;
    exports pbouda.jeffrey.server.core.web.controllers;
    exports pbouda.jeffrey.server.core.workspace;
    exports pbouda.jeffrey.server.core.workspace.consumer;
}
