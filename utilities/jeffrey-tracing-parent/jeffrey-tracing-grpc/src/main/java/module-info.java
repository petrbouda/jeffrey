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
 * Declared as a real module rather than an automatic one because Jeffrey's own gRPC client is a
 * named module and has to {@code requires} this by name.
 */
module cafe.jeffrey.jfr.events.grpc.interceptor {
    // The interceptors drive the JFR event lifecycle directly (isEnabled/begin/end/shouldCommit).
    requires jdk.jfr;
    requires cafe.jeffrey.jfr.events;
    requires io.grpc;
    requires com.google.protobuf;

    exports cafe.jeffrey.jfr.events.grpc.interceptor;
}
