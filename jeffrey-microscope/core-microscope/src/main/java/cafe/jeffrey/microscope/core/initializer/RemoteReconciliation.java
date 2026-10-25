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

package cafe.jeffrey.microscope.core.initializer;

import cafe.jeffrey.microscope.core.configuration.properties.ConfiguredRemoteProperties.DesiredRemote;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.model.remote.RemoteSource;

import java.time.Instant;
import java.util.Optional;

/**
 * What startup does to the stored connection so that it matches the configuration: decided from
 * the declaration and the stored row alone, with no remote contacted.
 */
sealed interface RemoteReconciliation {

    /** The stored connection already is what configuration wants, or configuration wants nothing. */
    record Keep() implements RemoteReconciliation {
    }

    /**
     * Connect {@code target}, replacing {@code previous} — whose cached clients must go when it was
     * reached at another address.
     */
    record Save(RemoteInfo target, Optional<RemoteInfo> previous) implements RemoteReconciliation {
    }

    /** Configuration no longer declares the remote it once connected. */
    record Disconnect(RemoteInfo previous) implements RemoteReconciliation {
    }

    /**
     * A remote the UI connected is left alone while configuration declares none, and replaced
     * when it declares one: configuration always wins. Moving a declared remote to another place
     * starts its connection afresh; renaming it, or flipping whether it may be changed, keeps it.
     */
    static RemoteReconciliation of(Optional<DesiredRemote> desired, Optional<RemoteInfo> stored, Instant now) {
        if (desired.isEmpty()) {
            return stored.filter(remote -> remote.source() == RemoteSource.CONFIG)
                    .<RemoteReconciliation>map(Disconnect::new)
                    .orElseGet(Keep::new);
        }
        DesiredRemote declared = desired.get();
        Instant createdAt = stored
                .filter(remote -> remote.address().identity().equals(declared.address().identity()))
                .map(RemoteInfo::createdAt)
                .orElse(now);
        RemoteInfo target = new RemoteInfo(declared.name(), declared.address(), createdAt, RemoteSource.CONFIG);
        if (stored.filter(target::equals).isPresent()) {
            return new Keep();
        }
        return new Save(target, stored);
    }
}
