/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import RecordingSession from '@hubs/services/api/model/RecordingSession.ts';
import RecordingStatus from '@hubs/services/api/model/RecordingStatus.ts';
import RepositoryFile from '@hubs/services/api/model/RepositoryFile.ts';

/**
 * Whether a set of ticked files may be downloaded together.
 *
 * Downloading several recording files merges them into one recording, and merging is
 * concatenation: the chunks are written one after another and nothing in the result records that
 * one was skipped. A recording built from chunks 1 and 3 therefore claims the span of 1 to 3 while
 * holding two thirds of it, and every rate read off it is wrong by the size of the hole. So the
 * chunks have to be an unbroken run.
 *
 * The Hub refuses a gapped selection itself — this is the same rule, said before the request is
 * made so the reader finds out while looking at the list rather than afterwards. It mirrors
 * `ChunkWindow` on the Java side field for field: finished recording files only, ordered by
 * `createdAt`. Deleting files has no such rule, which is why this constrains the Download button
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
    'A download merges them into one recording, so they have to be an unbroken run — ' +
    'select the recordings in between as well, or download them separately.'
  );
}
