/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
import GCOverviewData from '@/services/api/model/GCOverviewData';
import GCConfigurationData from '@/services/api/model/GCConfigurationData';
import GCTimeseriesType from '@/services/api/model/GCTimeseriesType';
import TimeseriesData from '@/services/timeseries/model/TimeseriesData';

export default class ProfileGCClient extends BaseProfileClient {

    constructor(profileId: string) {
        super(profileId, 'gc');
    }

    public getOverview(): Promise<GCOverviewData> {
        return this.get<GCOverviewData>('');
    }

    public getTimeseries(timeseriesType: GCTimeseriesType): Promise<TimeseriesData> {
        return this.get<TimeseriesData>('/timeseries', { timeseriesType });
    }

    public getConfiguration(): Promise<GCConfigurationData> {
        return this.get<GCConfigurationData>('/configuration');
    }
}
