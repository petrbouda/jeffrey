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

package cafe.jeffrey.microscope.persistence.api;

import cafe.jeffrey.microscope.model.remote.RemoteInfo;

import java.util.Optional;

/**
 * The one remote this Microscope is connected to, as a single stored row. Its workspaces and
 * projects are not stored — they are read live from the remote.
 */
public interface RemoteRepository {

    Optional<RemoteInfo> find();

    /** Connects to {@code remote}, replacing whatever was connected before. */
    void save(RemoteInfo remote);

    /** Forgets the connection; nothing on the remote is touched. */
    void delete();
}
