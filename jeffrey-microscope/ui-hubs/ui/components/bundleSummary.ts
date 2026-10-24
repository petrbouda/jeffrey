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

import type { Variant } from '@shared/types/ui';
import type Bundle from '../services/api/model/Bundle';
import type Recording from '../services/api/model/Recording';
import { hasFailed, isInitializing } from './profileInitChips';
import { recordingKindOf, type RecordingKindKey } from './recordingKinds';

/**
 * What a bundle's collapsed row says about it: one badge per item with the item's state, the item
 * and file counts, and the size of everything it holds.
 */

/** The drag payload a group chip reads to tell a bundle from a recording card. */
export const BUNDLE_DRAG_PREFIX = 'bundle:';

/** Where an item sits in its bundle: the recording first, as the hubs_download answer lists it. */
const KIND_ORDER: Record<RecordingKindKey, number> = {
  JFR: 0,
  PPROF: 0,
  OTLP: 0,
  PERF: 1,
  HEAP: 2
};

/** A bundle's items in the order its card lists them: by kind, then by name. */
export function compareMembers(a: Recording, b: Recording): number {
  const byKind =
    KIND_ORDER[recordingKindOf(a.eventSource).key] - KIND_ORDER[recordingKindOf(b.eventSource).key];
  return byKind || a.filename.localeCompare(b.filename);
}

export type MemberState = 'built' | 'running' | 'failed' | 'new';

const STATE_TITLES: Record<MemberState, string> = {
  built: 'initialized',
  running: 'initializing',
  failed: 'initialization failed',
  new: 'not initialized'
};

export interface MemberBadge {
  id: string;
  label: string;
  variant: Variant;
  state: MemberState;
  title: string;
}

/**
 * @param analyzing whether the item's Analyze request is still in flight, which a heap dump shows
 *                  as running before its first stage is reported
 */
export function memberState(member: Recording, analyzing: boolean): MemberState {
  if (isInitializing(member.initProgress) || analyzing) {
    return 'running';
  }
  if (hasFailed(member.initProgress)) {
    return 'failed';
  }
  return member.hasProfile ? 'built' : 'new';
}

export function memberBadges(members: Recording[], analyzing: ReadonlySet<string>): MemberBadge[] {
  return members.map(member => {
    const kind = recordingKindOf(member.eventSource);
    const state = memberState(member, analyzing.has(member.id));
    return {
      id: member.id,
      label: kind.label,
      variant: kind.badgeVariant,
      state,
      title: `${kind.name} · ${STATE_TITLES[state]}`
    };
  });
}

/** Every item's files and the bundle's own files together. */
export function bundleSize(bundle: Bundle, members: Recording[]): number {
  return (
    members.reduce((sum, member) => sum + member.sizeInBytes, 0) +
    bundle.files.reduce((sum, file) => sum + file.sizeInBytes, 0)
  );
}

/** "3 items · 1 file", the file count left out when the bundle holds none. */
export function bundleItemsLabel(bundle: Bundle, members: Recording[]): string {
  const items = members.length;
  const files = bundle.files.length;
  const itemsText = `${items} item${items === 1 ? '' : 's'}`;
  return files === 0 ? itemsText : `${itemsText} · ${files} file${files === 1 ? '' : 's'}`;
}
