/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
