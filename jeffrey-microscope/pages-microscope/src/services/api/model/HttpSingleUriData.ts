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

import HttpHeader from '@/services/api/model/HttpHeader.ts';
import HttpUriInfo from '@/services/api/model/HttpUriInfo.ts';
import HttpStatusStats from '@/services/api/model/HttpStatusStats.ts';
import HttpMethodStats from '@/services/api/model/HttpMethodStats.ts';
import HttpSlowRequest from '@/services/api/model/HttpSlowRequest.ts';
import Serie from '@/services/timeseries/model/Serie.ts';

export default class HttpSingleUriData {
  constructor(
    public header: HttpHeader,
    public uri: HttpUriInfo,
    public statusCodes: HttpStatusStats[],
    public methods: HttpMethodStats[],
    public slowRequests: HttpSlowRequest[],
    public responseTimeSerie: Serie,
    public requestCountSerie: Serie
  ) {}
}
