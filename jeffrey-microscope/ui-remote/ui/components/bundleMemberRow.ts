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

import type { SplitButtonTone } from '@shared/components/SplitButton.vue';
import type Recording from '../services/api/model/Recording';
import { TYPE_GROUP_DISPLAY, typeGroupOf } from '../services/sessionFiles';
import type { MemberState } from './bundleSummary';
import { recordingKindOf } from './recordingKinds';

/**
 * What an item of a bundle shows as its folder row, by the rule a remote session's detail follows:
 * the folder is named after the item's file type ("JFR Recordings", "Perf Counters") and opens to
 * list its files under their own names, a single file included.
 */
export interface MemberRowLook {
  title: string;
  fileType: string;
  fileCount: number;
}

export function memberRowLook(member: Recording): MemberRowLook {
  const files = member.files ?? [];
  const fileType = files.length > 0 ? files[0].type : recordingKindOf(member.eventSource).fileType;
  const display = TYPE_GROUP_DISPLAY[typeGroupOf(fileType)];
  return { title: display.name, fileType: display.fileType, fileCount: files.length };
}

/** What the item's split button does when its main part is clicked. */
export type MemberActionKind = 'initialize' | 'open' | 'retry' | 'running';

export interface MemberAction {
  kind: MemberActionKind;
  label: string;
  icon: string;
  tone: SplitButtonTone;
  /** A run in progress: nothing to click until it ends. */
  disabled: boolean;
}

const ACTIONS: Record<MemberState, MemberAction> = {
  new: {
    kind: 'initialize',
    label: 'Initialize',
    icon: 'bi-play-fill',
    tone: 'primary',
    disabled: false
  },
  running: {
    kind: 'running',
    label: 'Initializing…',
    icon: 'bi-hourglass-split',
    tone: 'primary',
    disabled: true
  },
  built: {
    kind: 'open',
    label: 'Open profile',
    icon: 'bi-check-circle-fill',
    tone: 'success',
    disabled: false
  },
  failed: {
    kind: 'retry',
    label: 'Retry',
    icon: 'bi-arrow-clockwise',
    tone: 'danger',
    disabled: false
  }
};

/** The main action of an item in the given state: initialize it, open its profile, or retry. */
export function memberAction(state: MemberState): MemberAction {
  return ACTIONS[state];
}
