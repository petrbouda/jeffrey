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

import RecordingSession from '@hubs/services/api/model/RecordingSession.ts';
import RecordingStatus from '@hubs/services/api/model/RecordingStatus.ts';
import RepositoryFile from '@hubs/services/api/model/RepositoryFile.ts';

/**
 * Whether a set of ticked files may be downloaded together.
 *
 * Several recording files become one recording — kept as the several files they are, but reporting
 * one span across them. Nothing in that span records that a chunk was skipped: a recording built
 * from chunks 1 and 3 claims the span of 1 to 3 while holding two thirds of it, and every rate read
 * off it is wrong by the size of the hole. So the chunks have to be an unbroken run.
 *
 * Said before the request is made, so the reader finds out while looking at the list rather than
 * afterwards. It mirrors `ChunkWindow` on the Java side field for field: finished recording files
 * only, ordered by `createdAt`. Deleting files has no such rule, which is why this constrains the
 * Download button
 * rather than the ticking.
 */

/** The session's finished recording chunks, oldest first — `ChunkWindow.finishedChunks`. */
export function orderedChunks(session: RecordingSession): RepositoryFile[] {
  return session.files
    .filter(file => file.isRecording && file.status === RecordingStatus.FINISHED)
    .slice()
    .sort((a, b) => a.createdAt - b.createdAt);
}

/**
 * The chunks lying between the first and last ticked one that were not ticked themselves — the
 * gap, named so the reader can be told which files to add.
 *
 * Artifacts (heap dumps, logs, perf counters) are not chunks and are ignored: they may be picked
 * freely alongside any run.
 */
export function chunkSelectionGap(
  session: RecordingSession,
  selected: { [fileId: string]: boolean } | undefined
): RepositoryFile[] {
  if (!selected) {
    return [];
  }

  const chunks = orderedChunks(session);
  const first = chunks.findIndex(chunk => selected[chunk.id]);
  if (first < 0) {
    return [];
  }

  let last = first;
  for (let i = chunks.length - 1; i > first; i--) {
    if (selected[chunks[i].id]) {
      last = i;
      break;
    }
  }

  return chunks.slice(first, last + 1).filter(chunk => !selected[chunk.id]);
}

/**
 * Whether the ticked chunks are an unbroken run. An empty selection and a single chunk are
 * trivially unbroken, so this only ever blocks a genuinely gapped pick — the Download button is
 * separately disabled when nothing is ticked at all.
 */
export function isContiguousChunkSelection(
  session: RecordingSession,
  selected: { [fileId: string]: boolean } | undefined
): boolean {
  return chunkSelectionGap(session, selected).length === 0;
}

/** The sentence shown beside a blocked Download, naming the files that would close the gap. */
export function chunkSelectionGapMessage(
  session: RecordingSession,
  selected: { [fileId: string]: boolean } | undefined
): string {
  const gap = chunkSelectionGap(session, selected);
  if (gap.length === 0) {
    return '';
  }

  const names = gap.map(file => file.name).join(', ');
  const subject = gap.length === 1 ? `${names} lies` : `${names} lie`;
  return (
    `The selected recordings are not next to each other: ${subject} between them. ` +
    'They become one recording reporting one span, so they have to be an unbroken run — ' +
    'select the recordings in between as well, or download them separately.'
  );
}
