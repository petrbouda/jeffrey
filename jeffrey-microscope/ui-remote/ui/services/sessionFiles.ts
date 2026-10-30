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

import type RecordingFile from '@remote/services/api/model/RecordingFile.ts';
import RecordingFileType from '@remote/services/api/model/RecordingFileType.ts';
import type RepositoryFile from '@remote/services/api/model/RepositoryFile.ts';
import type { Variant } from '@shared/types/ui';

/**
 * How a session's files are laid out on screen: one folder per file type, named after the type and
 * opened to list its files, a single file included, so every type reads the same; a rotated
 * `.jfr.N` sibling folds under the chunk it belongs to. The Recordings page lays out a bundle's
 * files and a recording's own files by the same rules, so a type is named and grouped the same in
 * both places.
 */

/**
 * What the layout reads from a file. A remote session's {@link RepositoryFile} is one as it is; a
 * recording's {@link RecordingFile} becomes one through {@link fromRecordingFile}.
 */
export interface SessionFile {
  id: string;
  name: string;
  size: number;
  fileType: RecordingFileType;
  isRecording: boolean;
  /** Epoch millis; absent where the file's own time is not known. */
  createdAt?: number;
}

export type ArtifactTypeGroup =
  | 'JFR_RECORDING'
  | 'HEAP_DUMP'
  | 'PERF_COUNTERS'
  | 'PPROF'
  | 'OTLP_PROFILE'
  | 'JVM_LOG'
  | 'APP_LOG'
  | 'HS_JVM_ERROR_LOG'
  | 'UNKNOWN';

export interface TypeGroupDisplay {
  name: string;
  variant: Variant;
  fileType: string;
}

/** A chunk and the rotated files written beside it. */
export interface FileGroupEntry<F extends SessionFile = RepositoryFile> {
  primary: F;
  children: F[];
  totalSize: number;
}

export interface TypeGroupPanel<F extends SessionFile = RepositoryFile> {
  groupKey: ArtifactTypeGroup;
  display: TypeGroupDisplay;
  files: F[];
  totalSize: number;
}

export const RECORDING_GROUP: ArtifactTypeGroup = 'JFR_RECORDING';

const TYPE_GROUP_ORDER: ArtifactTypeGroup[] = [
  'JFR_RECORDING',
  'HEAP_DUMP',
  'PERF_COUNTERS',
  'PPROF',
  'OTLP_PROFILE',
  'JVM_LOG',
  'APP_LOG',
  'HS_JVM_ERROR_LOG',
  'UNKNOWN'
];

const FILE_TYPE_TO_GROUP: Record<string, ArtifactTypeGroup> = {
  [RecordingFileType.JFR]: 'JFR_RECORDING',
  [RecordingFileType.JFR_LZ4]: 'JFR_RECORDING',
  [RecordingFileType.ASPROF]: 'JFR_RECORDING',
  [RecordingFileType.HEAP_DUMP]: 'HEAP_DUMP',
  [RecordingFileType.HEAP_DUMP_GZ]: 'HEAP_DUMP',
  [RecordingFileType.PERF_COUNTERS]: 'PERF_COUNTERS',
  [RecordingFileType.PPROF]: 'PPROF',
  [RecordingFileType.OTLP_PROFILE]: 'OTLP_PROFILE',
  [RecordingFileType.JVM_LOG]: 'JVM_LOG',
  [RecordingFileType.APP_LOG]: 'APP_LOG',
  [RecordingFileType.HS_JVM_ERROR_LOG]: 'HS_JVM_ERROR_LOG',
  [RecordingFileType.UNKNOWN]: 'UNKNOWN'
};

export const TYPE_GROUP_DISPLAY: Record<ArtifactTypeGroup, TypeGroupDisplay> = {
  JFR_RECORDING: { name: 'JFR Recordings', variant: 'primary', fileType: 'JFR' },
  HEAP_DUMP: { name: 'Heap Dumps', variant: 'purple', fileType: 'HEAP_DUMP' },
  PERF_COUNTERS: { name: 'Perf Counters', variant: 'blue', fileType: 'PERF_COUNTERS' },
  PPROF: { name: 'pprof Profiles', variant: 'teal', fileType: 'PPROF' },
  OTLP_PROFILE: { name: 'OpenTelemetry Profiles', variant: 'orange', fileType: 'OTLP_PROFILE' },
  JVM_LOG: { name: 'JVM Logs', variant: 'green', fileType: 'JVM_LOG' },
  APP_LOG: { name: 'Application Logs', variant: 'brown', fileType: 'APP_LOG' },
  HS_JVM_ERROR_LOG: { name: 'HotSpot Error Logs', variant: 'red', fileType: 'HS_JVM_ERROR_LOG' },
  UNKNOWN: { name: 'Other Files', variant: 'grey', fileType: 'UNKNOWN' }
};

/** The file types a recording's chunks come in; every other type is an artifact beside them. */
const RECORDING_FILE_TYPES: Set<string> = new Set([
  RecordingFileType.JFR,
  RecordingFileType.JFR_LZ4
]);

const ROTATED_FILENAME = /^(.+)\.(\d+)$/;

/** A recording's file in the layout's terms; a recording's files carry no time of their own. */
export function fromRecordingFile(file: RecordingFile): SessionFile {
  return {
    id: file.id,
    name: file.filename,
    size: file.sizeInBytes,
    fileType: file.type,
    isRecording: RECORDING_FILE_TYPES.has(file.type)
  };
}

/** The group a file type is listed under; a type the layout does not know is one of the others. */
export function typeGroupOf(fileType: string): ArtifactTypeGroup {
  return FILE_TYPE_TO_GROUP[fileType] ?? 'UNKNOWN';
}

function isRecordingOrTempFile(file: SessionFile): boolean {
  return file.isRecording || file.fileType === RecordingFileType.ASPROF;
}

function sumSizes(files: SessionFile[]): number {
  return files.reduce((sum, file) => sum + file.size, 0);
}

/**
 * Artifacts first, newest first; then the recordings by name, newest chunk first — a chunk's
 * name carries its start, which a stat of a growing file may not. Files without a time keep the
 * order they came in.
 */
export function sortSessionFiles<F extends SessionFile>(files: F[]): F[] {
  const priority = (file: F): number => (isRecordingOrTempFile(file) ? 1 : 0);
  return [...files].sort((a, b) => {
    if (priority(a) !== priority(b)) {
      return priority(a) - priority(b);
    }
    if (priority(a) === 1) {
      return b.name.localeCompare(a.name);
    }
    return (b.createdAt ?? 0) - (a.createdAt ?? 0);
  });
}

/** The session's recordings, async-profiler's scratch file included, newest chunk first. */
export function recordingFiles<F extends SessionFile>(files: F[]): F[] {
  return sortSessionFiles(files).filter(isRecordingOrTempFile);
}

function parseRotatedFilename(filename: string): { baseName: string; suffix: number } | null {
  const match = filename.match(ROTATED_FILENAME);
  if (!match) {
    return null;
  }
  return { baseName: match[1], suffix: parseInt(match[2], 10) };
}

/** Folds each rotated `name.N` file under the file named `name`, keeping the given order. */
export function groupRotatedFiles<F extends SessionFile>(sortedFiles: F[]): FileGroupEntry<F>[] {
  const nameSet = new Set(sortedFiles.map(f => f.name));
  const grouped = new Map<string, FileGroupEntry<F>>();
  const assigned = new Set<string>();

  for (const file of sortedFiles) {
    const parsed = parseRotatedFilename(file.name);
    if (!parsed || !nameSet.has(parsed.baseName)) {
      continue;
    }
    if (!grouped.has(parsed.baseName)) {
      const primary = sortedFiles.find(f => f.name === parsed.baseName);
      if (primary) {
        grouped.set(parsed.baseName, { primary, children: [], totalSize: primary.size });
        assigned.add(primary.id);
      }
    }
    const group = grouped.get(parsed.baseName);
    if (group) {
      group.children.push(file);
      group.totalSize += file.size;
      assigned.add(file.id);
    }
  }

  for (const group of grouped.values()) {
    group.children.sort(
      (a, b) =>
        (parseRotatedFilename(a.name)?.suffix ?? 0) - (parseRotatedFilename(b.name)?.suffix ?? 0)
    );
  }

  const result: FileGroupEntry<F>[] = [];
  for (const file of sortedFiles) {
    if (!assigned.has(file.id)) {
      result.push({ primary: file, children: [], totalSize: file.size });
    } else if (grouped.has(file.name)) {
      result.push(grouped.get(file.name)!);
      grouped.delete(file.name);
    }
  }
  return result;
}

function filesByGroup<F extends SessionFile>(files: F[]): Map<ArtifactTypeGroup, F[]> {
  const groups = new Map<ArtifactTypeGroup, F[]>();
  for (const file of sortSessionFiles(files)) {
    const groupKey = typeGroupOf(file.fileType);
    if (!groups.has(groupKey)) {
      groups.set(groupKey, []);
    }
    groups.get(groupKey)!.push(file);
  }
  return groups;
}

function artifactsOnly<F extends SessionFile>(files: F[]): F[] {
  return files.filter(
    file => !isRecordingOrTempFile(file) && typeGroupOf(file.fileType) !== RECORDING_GROUP
  );
}

/** One folder per file type, recordings included, in display order. */
export function fileGroupPanels<F extends SessionFile>(files: F[]): TypeGroupPanel<F>[] {
  const groups = filesByGroup(files);
  const panels: TypeGroupPanel<F>[] = [];
  for (const groupKey of TYPE_GROUP_ORDER) {
    const grouped = groups.get(groupKey);
    if (!grouped) {
      continue;
    }
    panels.push({
      groupKey,
      display: TYPE_GROUP_DISPLAY[groupKey],
      files: grouped,
      totalSize: sumSizes(grouped)
    });
  }
  return panels;
}

/** One folder per artifact type, in display order; the recordings have a folder of their own. */
export function typeGroupPanels<F extends SessionFile>(files: F[]): TypeGroupPanel<F>[] {
  return fileGroupPanels(artifactsOnly(files));
}

export function totalSize(files: SessionFile[]): number {
  return sumSizes(files);
}
