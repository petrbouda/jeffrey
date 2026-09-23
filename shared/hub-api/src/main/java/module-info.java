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
// protobuf and the gRPC jars are automatic modules (named only by their jar manifests), so javac
// warns that re-exporting them is fragile. Re-exported deliberately: the generated stubs and
// messages ARE this module's exported API, and no caller can use them without these types.
@SuppressWarnings("requires-transitive-automatic")
module cafe.jeffrey.shared.hub.api {
    requires transitive com.google.protobuf;
    requires transitive io.grpc;
    requires transitive io.grpc.stub;
    requires io.grpc.protobuf;
    requires com.google.common;
    requires java.annotation;

    exports cafe.jeffrey.hub.api.v1;
}
