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

package cafe.jeffrey.microscope.remote.client;

import cafe.jeffrey.microscope.grpc.client.*;

import cafe.jeffrey.microscope.model.remote.RemoteAddress;

import java.util.function.Function;

public record RemoteClients(
        DiscoveryClient discovery,
        RepositoryClient repository,
        FileStreamClient files,
        InstancesClient instances,
        ProjectsClient projects
) {

    @FunctionalInterface
    public interface Factory extends Function<RemoteAddress, RemoteClients> {

        /**
         * Lets go of whatever the factory holds for this address. A no-op for a factory that
         * holds nothing.
         */
        default void evict(RemoteAddress address) {
        }
    }
}
