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

import axios from "axios";
import HttpUtils from "@/services/HttpUtils";
import GlobalVars from "@/services/GlobalVars";
import EventSummary from "@/services/flamegraphs/model/EventSummary";

export default abstract class EventSummariesClient {

    public static primary(workspaceId: string, projectId: string, profileId: string): Promise<EventSummary[]> {
        let baseUrl = GlobalVars.internalUrl + '/workspaces/' + workspaceId + '/projects/' + projectId + '/profiles/' + profileId + '/flamegraph';
        return EventSummariesClient.eventSummaries(baseUrl);
    }

    public static differential(workspaceId: string, projectId: string, primaryProfileId: string, secondaryProfileId: string): Promise<EventSummary[]> {
        let baseUrl = GlobalVars.internalUrl + '/workspaces/' + workspaceId + '/projects/' + projectId + '/profiles/' + primaryProfileId + '/diff/' + secondaryProfileId + '/differential-flamegraph'
        return EventSummariesClient.eventSummaries(baseUrl);
    }

    private static eventSummaries(baseUrl: string): Promise<EventSummary[]> {
        return axios.get<EventSummary[]>(baseUrl + '/events', HttpUtils.JSON_ACCEPT_HEADER)
            .then(HttpUtils.RETURN_DATA)
    }
}
