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
import { memberAction, memberRowLook } from './bundleMemberRow';

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
  it('names an item read from several files after their type', () => {
    const look = memberRowLook(
      member('JDK', [
        file('chunk-1.jfr.lz4', RecordingFileType.JFR_LZ4),
        file('chunk-2.jfr.lz4', RecordingFileType.JFR_LZ4)
      ])
    );
    expect(look).toEqual({ title: 'JFR Recordings', fileType: 'JFR', fileCount: 2 });
  });

  it('makes a folder of an item read from one file too', () => {
    const look = memberRowLook(
      member('PERF_COUNTERS', [file('perf-counters.hsperfdata', RecordingFileType.PERF_COUNTERS)])
    );
    expect(look).toEqual({
      title: 'Perf Counters',
      fileType: RecordingFileType.PERF_COUNTERS,
      fileCount: 1
    });
  });

  it('lists both heap dump types under one heap dump folder', () => {
    const look = memberRowLook(
      member('HEAP_DUMP', [file('heap.hprof.gz', RecordingFileType.HEAP_DUMP_GZ)])
    );
    expect(look.title).toBe('Heap Dumps');
    expect(look.fileType).toBe(RecordingFileType.HEAP_DUMP);
  });

  it("names an item listing no file after its kind's folder", () => {
    const look = memberRowLook(member('HEAP_DUMP', []));
    expect(look).toEqual({ title: 'Heap Dumps', fileType: 'HEAP_DUMP', fileCount: 0 });
  });
});

describe('memberAction', () => {
  it('offers to initialize an item never initialized', () => {
    expect(memberAction('new')).toMatchObject({ kind: 'initialize', disabled: false });
  });

  it('opens the profile of an initialized item, in the success tone', () => {
    expect(memberAction('built')).toMatchObject({
      kind: 'open',
      label: 'Open profile',
      tone: 'success'
    });
  });

  it('retries a failed run and waits out a running one', () => {
    expect(memberAction('failed')).toMatchObject({ kind: 'retry', tone: 'danger' });
    expect(memberAction('running')).toMatchObject({ kind: 'running', disabled: true });
  });
});
