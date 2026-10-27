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

package cafe.jeffrey.shared.notification;

/**
 * What a notification is about — the coarse bucket a reader filters by before looking at any one type.
 * <p>
 * These are Jeffrey's own nouns rather than a generic severity-adjacent taxonomy: what an operator asks
 * is "what happened to my recordings" or "what happened to that heap dump", and the answer should be
 * one filter away.
 */
public enum NotificationCategory {

    /** The recording store: uploads, downloads, deletions, and the files behind them. */
    RECORDING,

    /** A profile's own life: created, initialized, analyzed, deleted. */
    PROFILE,

    /** Heap dump ingestion and indexing. */
    HEAP_DUMP,

    /** Workspaces, local or held on a hub. */
    WORKSPACE,

    /** The connection to a hub, and anything that crosses it. */
    HUB,

    /** Everything about the process itself that fits nowhere above. */
    SYSTEM
}
