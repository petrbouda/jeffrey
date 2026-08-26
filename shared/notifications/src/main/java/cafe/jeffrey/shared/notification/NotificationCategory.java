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

    /** Model calls: the advisor, the assistants, the OQL helper. */
    AI,

    /** Everything about the process itself that fits nowhere above. */
    SYSTEM
}
