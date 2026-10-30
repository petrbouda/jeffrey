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

import { describe, expect, it } from 'vitest';
import type Recording from '../services/api/model/Recording';
import RecordingFile from '../services/api/model/RecordingFile';
import RecordingFileType from '../services/api/model/RecordingFileType';
import { memberRowLook } from './bundleMemberRow';

function member(eventSource: string, files: RecordingFile[]): Recording {
  return {
    id: 'r1',
    filename: 'checkout_2026-03-01T12-00-00Z',
    eventSource,
    files
  } as Recording;
}

const file = (name: string, type: RecordingFileType) => new RecordingFile(name, name, 10, type, '');

describe('memberRowLook', () => {
  it('names an item read from several files after their type, and lets it open', () => {
    const look = memberRowLook(
      member('JDK', [
        file('chunk-1.jfr.lz4', RecordingFileType.JFR_LZ4),
        file('chunk-2.jfr.lz4', RecordingFileType.JFR_LZ4)
      ])
    );
    expect(look).toEqual({
      title: 'JFR Recordings',
      fileType: 'JFR',
      grouped: true,
      fileCount: 2
    });
  });

  it('names an item read from one file after the file, with its own type', () => {
    const look = memberRowLook(
      member('PERF_COUNTERS', [
        file('checkout_part_perf-counters.hsperfdata', RecordingFileType.PERF_COUNTERS)
      ])
    );
    expect(look).toEqual({
      title: 'checkout_part_perf-counters.hsperfdata',
      fileType: RecordingFileType.PERF_COUNTERS,
      grouped: false,
      fileCount: 1
    });
  });

  it('falls back to the recording name and its kind for an item listing no file', () => {
    const look = memberRowLook(member('HEAP_DUMP', []));
    expect(look.title).toBe('checkout_2026-03-01T12-00-00Z');
    expect(look.fileType).toBe(RecordingFileType.HEAP_DUMP);
    expect(look.grouped).toBe(false);
  });
});
