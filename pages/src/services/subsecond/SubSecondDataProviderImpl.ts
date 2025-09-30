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

import SubSecondData from "@/services/subsecond/model/SubSecondData";
import SubSecondDataProvider from "@/services/subsecond/SubSecondDataProvider";
import GlobalVars from "@/services/GlobalVars";
import axios from "axios";
import HttpUtils from "@/services/HttpUtils";

export default class SubSecondDataProviderImpl implements SubSecondDataProvider {

    private readonly baseUrl: string;
    private readonly eventType: string;
    private readonly useWeight: boolean;

    constructor(projectId: string, profileId: string, eventType: string, useWeight: boolean) {
        this.baseUrl = GlobalVars.internalUrl + '/projects/' + projectId + '/profiles/' + profileId + '/subsecond'
        this.eventType = eventType;
        this.useWeight = useWeight;
    }

    provide(): Promise<SubSecondData> {
        const content = {
            eventType: this.eventType,
            useWeight: this.useWeight
        };

        return axios.post(this.baseUrl, content, HttpUtils.JSON_HEADERS)
            .then(HttpUtils.RETURN_DATA);
    }
}
