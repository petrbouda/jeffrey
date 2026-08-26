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
