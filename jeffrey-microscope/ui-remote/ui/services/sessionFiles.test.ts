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
import RecordingFile from '@remote/services/api/model/RecordingFile.ts';
import RecordingFileType from '@remote/services/api/model/RecordingFileType.ts';
import RepositoryFile from '@remote/services/api/model/RepositoryFile.ts';
import {
  fileGroupPanels,
  fromRecordingFile,
  groupRotatedFiles,
  recordingFiles,
  sortSessionFiles,
  standaloneArtifacts,
  standaloneFiles,
  totalSize,
  typeGroupOf,
  typeGroupPanels
} from '@remote/services/sessionFiles.ts';

const T0 = 1_750_000_000_000;
const MINUTE = 60_000;

function file(
  name: string,
  fileType: RecordingFileType,
  minute: number = 0,
  size: number = 100
): RepositoryFile {
  const isRecording = fileType === RecordingFileType.JFR || fileType === RecordingFileType.JFR_LZ4;
  return new RepositoryFile(name, name, T0 + minute * MINUTE, size, fileType, false, isRecording);
}

const chunk = (name: string, minute: number) => file(name, RecordingFileType.JFR_LZ4, minute);

describe('sortSessionFiles', () => {
  it('puts artifacts first, newest first, then recordings by name, newest chunk first', () => {
    const files = [
      chunk('recording-0900.jfr.lz4', 0),
      file('jvm.log', RecordingFileType.JVM_LOG, 1),
      chunk('recording-0905.jfr.lz4', 5),
      file('heap.hprof.gz', RecordingFileType.HEAP_DUMP_GZ, 3)
    ];
    expect(sortSessionFiles(files).map(f => f.name)).toEqual([
      'heap.hprof.gz',
      'jvm.log',
      'recording-0905.jfr.lz4',
      'recording-0900.jfr.lz4'
    ]);
  });
});

describe('recordingFiles', () => {
  it("keeps the chunks and async-profiler's scratch file, nothing else", () => {
    const files = [
      chunk('recording-0900.jfr.lz4', 0),
      file('asprof.tmp', RecordingFileType.ASPROF, 1),
      file('app.log', RecordingFileType.APP_LOG, 2)
    ];
    expect(recordingFiles(files).map(f => f.name)).toEqual([
      'recording-0900.jfr.lz4',
      'asprof.tmp'
    ]);
  });
});

describe('groupRotatedFiles', () => {
  it('folds rotated siblings under their file, in suffix order', () => {
    const files = [
      file('profile.jfr', RecordingFileType.JFR, 0, 10),
      file('profile.jfr.2', RecordingFileType.JFR, 2, 30),
      file('profile.jfr.1', RecordingFileType.JFR, 1, 20),
      file('other.jfr', RecordingFileType.JFR, 3, 5)
    ];
    const groups = groupRotatedFiles(files);
    expect(groups.map(g => g.primary.name)).toEqual(['profile.jfr', 'other.jfr']);
    expect(groups[0].children.map(c => c.name)).toEqual(['profile.jfr.1', 'profile.jfr.2']);
    expect(groups[0].totalSize).toBe(60);
    expect(groups[1].children).toEqual([]);
  });

  it('leaves a numbered file alone when no file carries its base name', () => {
    const groups = groupRotatedFiles([file('profile.jfr.1', RecordingFileType.JFR)]);
    expect(groups).toHaveLength(1);
    expect(groups[0].primary.name).toBe('profile.jfr.1');
  });
});

describe('typeGroupPanels and standaloneArtifacts', () => {
  const files = [
    chunk('recording-0900.jfr.lz4', 0),
    file('heap-1.hprof.gz', RecordingFileType.HEAP_DUMP_GZ, 1, 40),
    file('heap-2.hprof.gz', RecordingFileType.HEAP_DUMP_GZ, 2, 60),
    file('jvm.log', RecordingFileType.JVM_LOG, 3, 7),
    file('hs_err_pid1.log', RecordingFileType.HS_JVM_ERROR_LOG, 4, 3)
  ];

  it('folds types with several files, and logs even when single, into panels', () => {
    const panels = typeGroupPanels(files);
    expect(panels.map(p => p.groupKey)).toEqual(['HEAP_DUMP', 'JVM_LOG']);
    expect(panels[0].totalSize).toBe(100);
    expect(panels[1].files.map(f => f.name)).toEqual(['jvm.log']);
  });

  it('leaves the single file of any other type standing alone', () => {
    expect(standaloneArtifacts(files).map(f => f.name)).toEqual(['hs_err_pid1.log']);
  });

  it('never puts recordings in either', () => {
    const names = [
      ...typeGroupPanels(files).flatMap(p => p.files),
      ...standaloneArtifacts(files)
    ].map(f => f.name);
    expect(names).not.toContain('recording-0900.jfr.lz4');
  });
});

describe('fileGroupPanels and standaloneFiles', () => {
  const recordingFile = (name: string, type: RecordingFileType, size: number = 10) =>
    fromRecordingFile(new RecordingFile(name, name, size, type, ''));

  it("folds a recording's chunks into a panel of their own, unlike the artifact layout", () => {
    const files = [
      recordingFile('part-1.jfr', RecordingFileType.JFR, 10),
      recordingFile('part-2.jfr', RecordingFileType.JFR, 20)
    ];
    const panels = fileGroupPanels(files);
    expect(panels.map(p => p.groupKey)).toEqual(['JFR_RECORDING']);
    expect(panels[0].display.name).toBe('JFR Recordings');
    expect(panels[0].totalSize).toBe(30);
    expect(typeGroupPanels(files)).toEqual([]);
  });

  it('keeps a single file of a type alone and logs in a panel', () => {
    const files = [
      recordingFile('perf-counters.hsperfdata', RecordingFileType.PERF_COUNTERS),
      recordingFile('gc.jvm-log', RecordingFileType.JVM_LOG)
    ];
    expect(standaloneFiles(files).map(f => f.name)).toEqual(['perf-counters.hsperfdata']);
    expect(fileGroupPanels(files).map(p => p.groupKey)).toEqual(['JVM_LOG']);
  });
});

describe('fromRecordingFile', () => {
  it("reads a recording file in the layout's terms, with no time of its own", () => {
    const file = fromRecordingFile(
      new RecordingFile('f1', 'chunk.jfr.lz4', 42, RecordingFileType.JFR_LZ4, 'first chunk')
    );
    expect(file).toEqual({
      id: 'f1',
      name: 'chunk.jfr.lz4',
      size: 42,
      fileType: RecordingFileType.JFR_LZ4,
      isRecording: true,
      description: 'first chunk'
    });
  });

  it('marks every other type as an artifact', () => {
    const file = fromRecordingFile(
      new RecordingFile('f2', 'app.log', 1, RecordingFileType.APP_LOG, '')
    );
    expect(file.isRecording).toBe(false);
  });
});

describe('typeGroupOf', () => {
  it('lists both heap dump types under heap dumps', () => {
    expect(typeGroupOf(RecordingFileType.HEAP_DUMP)).toBe('HEAP_DUMP');
    expect(typeGroupOf(RecordingFileType.HEAP_DUMP_GZ)).toBe('HEAP_DUMP');
  });

  it('lists an unknown type under the other files', () => {
    expect(typeGroupOf('SOMETHING_NEW')).toBe('UNKNOWN');
  });
});

describe('totalSize', () => {
  it('adds the sizes', () => {
    expect(
      totalSize([file('a', RecordingFileType.JFR, 0, 3), file('b', RecordingFileType.JFR, 0, 4)])
    ).toBe(7);
  });
});
