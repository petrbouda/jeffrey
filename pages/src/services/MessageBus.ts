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
    static SUBSECOND_SELECTION_CLEAR = "subsecond-selection-clear"
    static SECONDARY_PROFILE_SELECTED = "secondary-profile-selected"

    static UPDATE_PROJECT_SETTINGS = "update-project-settings"

    static SIDEBAR_CHANGED = "sidebar-changed"
    static JOBS_COUNT_CHANGED = "jobs-count-changed"
    static PROFILES_COUNT_CHANGED = "profiles-count-changed"
    static RECORDINGS_COUNT_CHANGED = "recordings-count-changed"
    static REPOSITORY_STATUS_CHANGED = "repository-status-changed"

    static emit(type: string, content: any) {
        this.INSTANCE.emit(type, content)
    }

    static on(type: string, handler: any) {
        this.INSTANCE.on(type, handler)
    }

    static off(type: string) {
        this.INSTANCE.off(type)
    }
}
