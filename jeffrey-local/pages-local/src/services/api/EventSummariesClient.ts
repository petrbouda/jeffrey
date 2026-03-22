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

import BaseProfileClient from '@/services/api/BaseProfileClient';
import EventSummary from "@/services/api/model/EventSummary";

export default class EventSummariesClient extends BaseProfileClient {

    private constructor(profileId: string, featurePath: string) {
        super(profileId, featurePath);
    }

    /**
     * Create a client for primary profile event summaries.
     */
    static primary(profileId: string): EventSummariesClient {
        return new EventSummariesClient(profileId, 'flamegraph');
    }

    /**
     * Create a client for differential analysis event summaries.
     */
    static differential(primaryProfileId: string, secondaryProfileId: string): EventSummariesClient {
        return new EventSummariesClient(primaryProfileId, `diff/${secondaryProfileId}/differential-flamegraph`);
    }

    events(): Promise<EventSummary[]> {
        return super.get<EventSummary[]>('/events');
    }
}
