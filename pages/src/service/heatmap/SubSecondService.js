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

import GlobalVars from '@/service/GlobalVars';
import axios from 'axios';
import HttpUtils from '@/service/HttpUtils';
import CompressionUtils from "@/service/CompressionUtils";
import ReplaceableToken from "@/service/replace/ReplaceableToken";

export default class SubSecondService {

    constructor(projectId, primaryProfileId, secondaryProfileId, eventType, useWeight, generated) {
        this.projectId = projectId;
        this.primaryProfileId = primaryProfileId;
        this.secondaryProfileId = secondaryProfileId;
        this.eventType = eventType;
        this.useWeight = useWeight;
        this.generated = generated;
    }

    primaryStartup() {
        if (this.generated) {
            return this.#generateStatic(ReplaceableToken.SUBSECOND_PRIMARY)
        }

        return this.#startup(this.primaryProfileId);
    }

    secondaryStartup() {
        if (this.generated) {
            return this.#generateStatic(ReplaceableToken.SUBSECOND_SECONDARY)
        }

        return this.#startup(this.secondaryProfileId);
    }

    // Used for generated flamegraph (e.g. command-line tool)
    #generateStatic(token) {
        const data = CompressionUtils.decodeAndDecompress(token)
        return Promise.resolve(JSON.parse(data))
    }

    #startup(profileId) {
        const content = {
            eventType: this.eventType,
            useWeight: this.useWeight
        };

        return axios.post(GlobalVars.url + '/projects/' + this.projectId + '/profiles/' + profileId + '/subsecond', content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
