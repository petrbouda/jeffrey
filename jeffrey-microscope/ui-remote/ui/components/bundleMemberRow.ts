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

import type Recording from '../services/api/model/Recording';
import { TYPE_GROUP_DISPLAY, typeGroupOf } from '../services/sessionFiles';
import { recordingKindOf } from './recordingKinds';

/**
 * What an item of a bundle shows as its file row, by the rule a remote session's detail follows: an
 * item read from several files is named after their type ("JFR Recordings") and opens to list
 * them; an item read from one file is named after that file.
 */
export interface MemberRowLook {
  title: string;
  fileType: string;
  /** Whether the row stands for several files and opens to list them. */
  grouped: boolean;
  fileCount: number;
}

export function memberRowLook(member: Recording): MemberRowLook {
  const files = member.files ?? [];
  if (files.length > 1) {
    const display = TYPE_GROUP_DISPLAY[typeGroupOf(files[0].type)];
    return {
      title: display.name,
      fileType: display.fileType,
      grouped: true,
      fileCount: files.length
    };
  }
  if (files.length === 1) {
    return { title: files[0].filename, fileType: files[0].type, grouped: false, fileCount: 1 };
  }
  return {
    title: member.filename,
    fileType: recordingKindOf(member.eventSource).fileType,
    grouped: false,
    fileCount: 0
  };
}
