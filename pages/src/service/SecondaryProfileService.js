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

import { ref } from 'vue';

export default class SecondaryProfileService {
    static profile = ref(null);
    static fontStyle = {
        color: "#78909c",
        "font-weight": "bold"
    }

    static {
        let profile = this.get();
        if (profile) {
            SecondaryProfileService.profile.value = profile.name;
        }
    }

    static equals(id) {
        return SecondaryProfileService.get() != null && SecondaryProfileService.get().id === id
    }

    static update(profile) {
        sessionStorage.setItem('secondary-profile', JSON.stringify(profile));
        SecondaryProfileService.profile.value = profile.name;
    }

    static remove() {
        sessionStorage.removeItem('secondary-profile');
        SecondaryProfileService.profile.value = null;
    }

    static get() {
        return JSON.parse(sessionStorage.getItem('secondary-profile'));
    }

    static id() {
        if (SecondaryProfileService.get() != null) {
            return SecondaryProfileService.get().id
        } else {
            return null
        }
    }

    static name() {
        if (SecondaryProfileService.get() != null) {
            return SecondaryProfileService.profile.value.replace('.jfr', '')
        } else {
            return null
        }
    }
}
