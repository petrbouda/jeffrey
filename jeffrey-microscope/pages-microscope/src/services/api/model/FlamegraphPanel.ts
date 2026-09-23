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

import EventSummary from '@/services/api/model/EventSummary';

/** How a panel's weight value is formatted (mirrors the backend WeightKind). */
export type WeightKind = 'DURATION' | 'BYTES';

/** A boolean flamegraph-card setting: whether it is offered, and its initial checked state. */
export interface ToggleOption {
  applicable: boolean;
  defaultOn: boolean;
}

/** The "Use weight" setting: applicability, default, toggle label, and formatting kind. */
export interface WeightOption {
  applicable: boolean;
  defaultOn: boolean;
  label: string | null;
  kind: WeightKind;
}

/** Presentation-role flags used for route-based show/hide (never inferred from the event code). */
export interface Classification {
  method: boolean;
  nativeMemory: boolean;
  blocking: boolean;
}

/**
 * A single flamegraph card, fully described by the backend. The frontend renders the grid by looping
 * these descriptors — it no longer infers a card's category, title, weight, or toggles from the event
 * code. Mirrors the backend {@code FlamegraphPanel} record.
 */
export default interface FlamegraphPanel {
  section: string;
  order: number;
  title: string;
  color: string;
  icon: string;
  showType: boolean;
  threadMode: ToggleOption;
  weight: WeightOption;
  excludeNonJava: ToggleOption;
  excludeIdle: ToggleOption;
  onlyUnsafe: ToggleOption;
  classification: Classification;
  event: EventSummary;
}
