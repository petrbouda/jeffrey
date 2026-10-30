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
import type { RecordingProfile } from '../services/api/model/Recording';
import { hasFailed, isInitializing } from './profileInitChips';

/**
 * How the recordings list splits into its two columns. The left one holds every recording, always:
 * uploaded ones on their own and remote downloads as bundles with all of their items, initialized or
 * not, a download of a single item included. The right one holds the profiles: one row per profile
 * built from a recording, a
 * bundle's items among them, with no bundle around them. A recording initialized twice has two rows
 * there.
 */

/** A bundle with all of its items, as the group and search selection found it. */
export interface BundleWithMembers {
  bundle: Bundle;
  members: Recording[];
}

/** One row of the Recordings column: a recording on its own, or a bundle with its items. */
export type RecordingItem =
  | { kind: 'recording'; key: string; recording: Recording }
  | {
      kind: 'bundle';
      key: string;
      bundle: Bundle;
      /** Every item, for the bundle's summary. */
      members: Recording[];
      /** The items listed in the bundle: those the type filter shows. */
      listed: Recording[];
    };

/** One row of the Profiles column: a profile with the recording it was built from. */
export interface ProfileItem {
  key: string;
  recording: Recording;
  profile: RecordingProfile;
}

export interface RecordingColumns {
  recordings: RecordingItem[];
  profiles: ProfileItem[];
}

export interface ColumnSelection {
  recordings: Recording[];
  bundles: BundleWithMembers[];
  /** The type filter; null when every type is shown. */
  shows: ((recording: Recording) => boolean) | null;
}

/**
 * A profile whose initialization is still running, or ended badly, has nothing to open yet: its
 * stage chips and its error stay on the recording's card on the left.
 */
export function isBuilt(profile: RecordingProfile): boolean {
  return !isInitializing(profile.initProgress) && !hasFailed(profile.initProgress);
}

/** The profiles built from the recording that can be opened, newest first. */
export function builtProfiles(recording: Recording): RecordingProfile[] {
  return (recording.profiles ?? []).filter(isBuilt);
}

const recordingItem = (recording: Recording): RecordingItem => ({
  kind: 'recording',
  key: recording.id,
  recording
});

const uploadedAtOf = (item: RecordingItem): number =>
  item.kind === 'recording' ? item.recording.uploadedAt : item.bundle.createdAt;

const profileItems = (recording: Recording): ProfileItem[] =>
  builtProfiles(recording).map(profile => ({
    key: profile.profileId,
    recording,
    profile
  }));

/**
 * Left: newest upload first. Right: most recently initialized first, so a profile you just built
 * surfaces at the top. A bundle's files keep a bundle whose items the type filter hides on the left
 * only while no type filter is set, as they have no type.
 */
export function splitColumns(selection: ColumnSelection): RecordingColumns {
  const shows = selection.shows ?? (() => true);
  const recordings: RecordingItem[] = [];
  const profiles: ProfileItem[] = [];
  for (const recording of selection.recordings.filter(shows)) {
    recordings.push(recordingItem(recording));
    profiles.push(...profileItems(recording));
  }
  for (const { bundle, members } of selection.bundles) {
    const listed = members.filter(shows);
    profiles.push(...listed.flatMap(profileItems));
    const filesShown = bundle.files.length > 0 && selection.shows === null;
    if (listed.length > 0 || filesShown) {
      recordings.push({
        kind: 'bundle',
        key: bundle.id,
        bundle,
        members,
        listed
      });
    }
  }
  recordings.sort((a, b) => uploadedAtOf(b) - uploadedAtOf(a));
  profiles.sort((a, b) => b.profile.createdAt - a.profile.createdAt);
  return { recordings, profiles };
}
