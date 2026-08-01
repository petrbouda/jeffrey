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
  AdvisorPrompt,
  AdvisorRecommendation,
  AdvisorRunResult,
  AdvisorSettings,
  BatchAdvisorProgress
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

  /** Launches a batch over the given event types, or every available type when none are passed. */
  run(eventTypes: string[] = []): Promise<BatchAdvisorProgress> {
    return super.post<BatchAdvisorProgress>('/run', { eventTypes });
  }

  runProgress(): Promise<BatchAdvisorProgress> {
    // Polled on a timer while a run is in flight, so a transient failure must not raise a toast per tick.
    return super.get<BatchAdvisorProgress>('/run/progress', undefined, { suppressToast: true });
  }

  /** The last run's stored timeline (per-type, per-step durations), or null when it has never run. */
  runResult(): Promise<AdvisorRunResult | null> {
    return super.get<AdvisorRunResult | null>('/run/result');
  }

  /**
   * Throws away every derived artifact — reports, claims, cached prompts and the kept timeline — so the
   * next run starts from nothing. Refused by the backend while a run is in flight.
   */
  deleteResults(): Promise<void> {
    return super.post<void>('/delete-results', {});
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
}
