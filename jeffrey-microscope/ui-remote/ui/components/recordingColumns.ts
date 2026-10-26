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

import type Bundle from '../services/api/model/Bundle';
import type Recording from '../services/api/model/Recording';
import { isInitializing } from './profileInitChips';

/**
 * How the recordings list splits into its two columns. The left one holds what still has work in it:
 * recordings not initialized yet (or initializing), and bundles that have such items or files to
 * download. The right one holds only profiles: one row per initialized recording, a bundle's items
 * among them, with no bundle around them.
 */

/** A bundle with all of its items, as the group and search selection found it. */
export interface BundleWithMembers {
  bundle: Bundle;
  members: Recording[];
}

/** One row of a column: a recording on its own, or a bundle with the items still left to initialize. */
export type ListItem =
  | { kind: 'recording'; key: string; recording: Recording }
  | {
      kind: 'bundle';
      key: string;
      bundle: Bundle;
      /** Every item, for the bundle's summary. */
      members: Recording[];
      /** The items listed in the bundle: those not initialized yet. */
      pending: Recording[];
    };

export interface RecordingColumns {
  pending: ListItem[];
  initialized: ListItem[];
}

export interface ColumnSelection {
  recordings: Recording[];
  bundles: BundleWithMembers[];
  /** The type filter; null when every type is shown. */
  shows: ((recording: Recording) => boolean) | null;
}

/**
 * A recording whose profile row exists only because its initialization is running stays on the left
 * with its stage chips, not on the right among the profiles you can open.
 */
export function isBuilt(recording: Recording): boolean {
  return recording.hasProfile && !isInitializing(recording.initProgress);
}

const recordingItem = (recording: Recording): ListItem => ({
  kind: 'recording',
  key: recording.id,
  recording
});

const uploadedAtOf = (item: ListItem): number =>
  item.kind === 'recording' ? item.recording.uploadedAt : item.bundle.createdAt;

const initializedAtOf = (item: ListItem): number =>
  item.kind === 'recording' ? item.recording.profileCreatedAt || item.recording.uploadedAt : 0;

/**
 * Left: newest upload first. Right: most recently initialized first, so a profile you just built
 * surfaces at the top instead of sinking to its upload slot. A bundle's files keep it on the left only
 * while no type filter is set, as they have no type.
 */
export function splitColumns(selection: ColumnSelection): RecordingColumns {
  const shows = selection.shows ?? (() => true);
  const pending: ListItem[] = [];
  const initialized: ListItem[] = [];
  for (const recording of selection.recordings.filter(shows)) {
    (isBuilt(recording) ? initialized : pending).push(recordingItem(recording));
  }
  for (const { bundle, members } of selection.bundles) {
    const shown = members.filter(shows);
    initialized.push(...shown.filter(isBuilt).map(recordingItem));
    const waiting = shown.filter(member => !isBuilt(member));
    const filesShown = bundle.files.length > 0 && selection.shows === null;
    if (waiting.length > 0 || filesShown) {
      pending.push({ kind: 'bundle', key: bundle.id, bundle, members, pending: waiting });
    }
  }
  pending.sort((a, b) => uploadedAtOf(b) - uploadedAtOf(a));
  initialized.sort((a, b) => initializedAtOf(b) - initializedAtOf(a));
  return { pending, initialized };
}
