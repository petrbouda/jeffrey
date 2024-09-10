/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

import mitt from "mitt"

export default class MessageBus {

    static INSTANCE = mitt()

    static PROFILE_DIALOG_TOGGLE = "profile-dialog-toggle";
    static PROFILE_CARD_TOGGLE = "profile-card-toggle";
    static FLAMEGRAPH_CREATED = "flamegraph-created";
    static FLAMEGRAPH_CHANGED = "flamegraph-timeseries-range-changed";
    static FLAMEGRAPH_SEARCH = "flamegraph-search";
    static TIMESERIES_SEARCH = "timeseries-search"
    static TIMESERIES_RESET_SEARCH = "timeseries-reset-search"
    static SUBSECOND_SELECTION_CLEAR = "subsecond-selection-clear"

    static emit(type, content) {
        this.INSTANCE.emit(type, content)
    }

    static on(type, handler) {
        this.INSTANCE.on(type, handler)
    }

    static off(type) {
        this.INSTANCE.off(type)
    }
}
