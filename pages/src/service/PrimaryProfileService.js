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

import {ref} from 'vue';

export default class PrimaryProfileService {
    static profile = ref('<none>');
    static fontStyle = {
        color: "#6366F1",
        "font-weight": "bold"
    }

    static {
        let profile = this.get();
        if (profile) {
            PrimaryProfileService.profile.value = profile.name;
        }
    }

    static equals(id) {
        return PrimaryProfileService.get() != null && PrimaryProfileService.get().id === id
    }

    static update(profile) {
        sessionStorage.setItem('primary-profile', JSON.stringify(profile));
        PrimaryProfileService.profile.value = profile.name;
    }

    static get() {
        return JSON.parse(sessionStorage.getItem('primary-profile'));
    }

    static remove() {
        sessionStorage.removeItem('primary-profile');
        PrimaryProfileService.profile.value = null;
    }

    static id() {
        return PrimaryProfileService.get().id
    }

    static name() {
        return PrimaryProfileService.profile.value.replace('.jfr', '')
    }
}
