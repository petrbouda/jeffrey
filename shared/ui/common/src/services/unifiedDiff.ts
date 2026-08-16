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

/**
 * Parses a unified diff into lines a viewer can render, tracking the real file and line number each
 * line lands on. The line numbers are what make a proposed change reviewable: a diff without them is
 * a wall of text you cannot locate in your own source.
 */

export type DiffLineType = 'add' | 'del' | 'ctx' | 'hunk' | 'meta';

export interface DiffLine {
  type: DiffLineType;
  /** The line's text with its leading +/-/space marker removed. */
  text: string;
  /** Line number in the original file, or null for an added line. */
  oldLine: number | null;
  /** Line number in the patched file, or null for a removed line. */
  newLine: number | null;
  /** Repository-relative path of the file this line belongs to, or null before any file header. */
  file: string | null;
}

export interface DiffFile {
  path: string;
  added: number;
  removed: number;
}

export interface ParsedDiff {
  lines: DiffLine[];
  files: DiffFile[];
  added: number;
  removed: number;
}

const HUNK_HEADER = /^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@/;
const NEW_FILE_HEADER = '+++ ';
const OLD_FILE_HEADER = '--- ';
const DIFF_GIT_HEADER = 'diff --git ';
const NO_NEWLINE_MARKER = '\\';
const DEV_NULL = '/dev/null';

/**
 * Strips the `a/` or `b/` prefix `git apply -p1` expects, so the path reads as a repository path.
 */
function stripPathPrefix(raw: string): string {
  const path = raw.split('\t')[0].trim();
  if (path.startsWith('a/') || path.startsWith('b/')) {
    return path.slice(2);
  }
  return path;
}

function isMeta(line: string): boolean {
  return (
    line.startsWith(DIFF_GIT_HEADER) ||
    line.startsWith(OLD_FILE_HEADER) ||
    line.startsWith(NEW_FILE_HEADER) ||
    line.startsWith('index ') ||
    line.startsWith('new file mode ') ||
    line.startsWith('deleted file mode ') ||
    line.startsWith('similarity index ') ||
    line.startsWith('rename ')
  );
}

export function parseUnifiedDiff(patch: string): ParsedDiff {
  const lines: DiffLine[] = [];
  const files: DiffFile[] = [];
  if (!patch) {
    return { lines, files, added: 0, removed: 0 };
  }

  let file: string | null = null;
  let current: DiffFile | null = null;
  let oldLine = 0;
  let newLine = 0;

  for (const raw of patch.replace(/\n$/, '').split('\n')) {
    const hunk = HUNK_HEADER.exec(raw);
    if (hunk) {
      oldLine = Number(hunk[1]);
      newLine = Number(hunk[2]);
      lines.push({ type: 'hunk', text: raw, oldLine: null, newLine: null, file });
      continue;
    }

    if (isMeta(raw)) {
      // The `+++` header names the file the patched line numbers belong to; a deletion has none, so
      // fall back to the `---` side rather than losing the path entirely.
      if (raw.startsWith(NEW_FILE_HEADER)) {
        const path = stripPathPrefix(raw.slice(NEW_FILE_HEADER.length));
        if (path !== DEV_NULL) {
          file = path;
        }
      } else if (raw.startsWith(OLD_FILE_HEADER)) {
        const path = stripPathPrefix(raw.slice(OLD_FILE_HEADER.length));
        if (path !== DEV_NULL) {
          file = path;
        }
      }
      if (file !== null && (current === null || current.path !== file)) {
        current = { path: file, added: 0, removed: 0 };
        files.push(current);
      }
      lines.push({ type: 'meta', text: raw, oldLine: null, newLine: null, file });
      continue;
    }

    const marker = raw.charAt(0);
    const text = raw.slice(1);

    if (marker === '+') {
      lines.push({ type: 'add', text, oldLine: null, newLine, file });
      newLine += 1;
      if (current) {
        current.added += 1;
      }
    } else if (marker === '-') {
      lines.push({ type: 'del', text, oldLine, newLine: null, file });
      oldLine += 1;
      if (current) {
        current.removed += 1;
      }
    } else if (marker === NO_NEWLINE_MARKER) {
      lines.push({ type: 'meta', text: raw, oldLine: null, newLine: null, file });
    } else {
      // A context line, including the bare empty line git writes instead of a lone space.
      lines.push({ type: 'ctx', text, oldLine, newLine, file });
      oldLine += 1;
      newLine += 1;
    }
  }

  return {
    lines,
    files,
    added: files.reduce((sum, entry) => sum + entry.added, 0),
    removed: files.reduce((sum, entry) => sum + entry.removed, 0)
  };
}
