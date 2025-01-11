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
import ProfileInfo from "@/service/project/model/ProfileInfo";

export default class SecondaryProfileService {
    static profile = ref<ProfileInfo | null>(null);

    static {
        let profile = this.get();
        if (profile) {
            SecondaryProfileService.profile.value = profile;
        }
    }

    static equals(id: string) {
        let profile = SecondaryProfileService.get();
        return profile != null && profile.id === id
    }

    static update(profile: ProfileInfo) {
        sessionStorage.setItem('secondary-profile', JSON.stringify(profile));
        SecondaryProfileService.profile.value = profile;
    }

    static remove() {
        sessionStorage.removeItem('secondary-profile');
        SecondaryProfileService.profile.value = null;
    }

    static get(): ProfileInfo | null {
        let item = sessionStorage.getItem('secondary-profile');
        if (item != null) {
            return JSON.parse(item);
        } else {
            return null
        }
    }

    static id(): string | null {
        let profile = SecondaryProfileService.get();
        if (profile != null) {
            return profile.id
        } else {
            return null
        }
    }

    static projectId(): string | null {
        let profile = SecondaryProfileService.get();
        if (profile != null) {
            return profile.projectId
        } else {
            return null
        }
    }

    static name(): string | null {
        let profile = SecondaryProfileService.get();
        if (profile != null) {
            return profile.name.replace('.jfr', '')
        } else {
            return null
        }
    }
}
