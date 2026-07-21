/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
import FlamegraphPanel from '@/services/api/model/FlamegraphPanel';

/**
 * Event-summaries client for pprof profiles. Same interface as {@link EventSummariesClient}
 * ({@code events()}), but points at the pprof-specific controller path — the response carries the
 * backend-resolved {@code category}, so the pprof-vs-JFR event-type mapping (a CPU profile is
 * {@code pprof.cpu}, not {@code jdk.ExecutionSample}) lives on the server, not in the client.
 */
export default class PprofEventSummariesClient extends BaseProfileClient {
  private constructor(profileId: string, featurePath: string) {
    super(profileId, featurePath);
  }

  static primary(profileId: string): PprofEventSummariesClient {
    return new PprofEventSummariesClient(profileId, 'pprof/flamegraph');
  }

  static differential(
    primaryProfileId: string,
    secondaryProfileId: string
  ): PprofEventSummariesClient {
    return new PprofEventSummariesClient(
      primaryProfileId,
      `pprof/diff/${secondaryProfileId}/differential-flamegraph`
    );
  }

  panels(): Promise<FlamegraphPanel[]> {
    return super.get<FlamegraphPanel[]>('/panels');
  }
}
