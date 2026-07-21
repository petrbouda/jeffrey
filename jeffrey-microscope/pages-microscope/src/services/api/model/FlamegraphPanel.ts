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
