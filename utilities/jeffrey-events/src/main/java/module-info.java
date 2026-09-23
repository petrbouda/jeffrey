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
module cafe.jeffrey.jfr.events {
    requires jdk.jfr;

    exports cafe.jeffrey.jfr.events;
    exports cafe.jeffrey.jfr.events.grpc;
    exports cafe.jeffrey.jfr.events.http;
    exports cafe.jeffrey.jfr.events.jdbc.pool;
    exports cafe.jeffrey.jfr.events.jdbc.statement;
    exports cafe.jeffrey.jfr.events.notification;
    exports cafe.jeffrey.jfr.events.trace;

    // JFR rewrites event-class bytecode at registration through
    // MethodHandles.privateLookupIn(eventClass, ...); the event packages
    // must be open to jdk.jfr or commit() fails with IllegalAccessException.
    opens cafe.jeffrey.jfr.events.grpc to jdk.jfr;
    opens cafe.jeffrey.jfr.events.http to jdk.jfr;
    opens cafe.jeffrey.jfr.events.jdbc.pool to jdk.jfr;
    opens cafe.jeffrey.jfr.events.jdbc.statement to jdk.jfr;
    opens cafe.jeffrey.jfr.events.notification to jdk.jfr;
    opens cafe.jeffrey.jfr.events.trace to jdk.jfr;
}
