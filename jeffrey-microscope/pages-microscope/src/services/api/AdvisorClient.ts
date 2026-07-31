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
import type {
  AdvisorEventType,
  AdvisorGenerateResponse,
  AdvisorProgress,
  AdvisorPrompt,
  AdvisorRecommendation,
  AdvisorSettings,
  RegressionComparison
} from '@/services/api/model/Advisor';

export default class AdvisorClient extends BaseProfileClient {
  constructor(profileId: string) {
    super(profileId, 'advisor');
  }

  eventTypes(): Promise<AdvisorEventType[]> {
    return super.get<AdvisorEventType[]>('/event-types');
  }

  /** Cached prompts only — opening the page never triggers a call tree walk nobody asked for. */
  prompts(): Promise<AdvisorPrompt[]> {
    return super.get<AdvisorPrompt[]>('/prompts');
  }

  /** Rebuilds every prompt, which is how a changed prune threshold reaches the model. */
  regeneratePrompts(): Promise<AdvisorPrompt[]> {
    return super.post<AdvisorPrompt[]>('/prompts', {});
  }

  recommendations(): Promise<AdvisorRecommendation[]> {
    return super.get<AdvisorRecommendation[]>('/recommendations');
  }

  generate(eventType: string): Promise<AdvisorGenerateResponse> {
    return super.post<AdvisorGenerateResponse>('/generate', { eventType });
  }

  progress(): Promise<AdvisorProgress> {
    // Polled on a timer while a run is in flight, so a transient failure must not raise a toast per tick.
    return super.get<AdvisorProgress>('/progress', undefined, { suppressToast: true });
  }

  /** The stage sequence, served by the backend so the timeline cannot drift from the pipeline. */
  stages(): Promise<string[]> {
    return super.get<string[]>('/stages');
  }

  cancel(): Promise<void> {
    return super.delete<void>('/run');
  }

  settings(): Promise<AdvisorSettings> {
    return super.get<AdvisorSettings>('/settings');
  }

  saveSettings(settings: Partial<AdvisorSettings>): Promise<AdvisorSettings> {
    return super.put<AdvisorSettings>('/settings', settings);
  }

  regression(baselineProfileId: string, eventType: string): Promise<RegressionComparison> {
    return super.get<RegressionComparison>('/regression', { baselineProfileId, eventType });
  }
}
