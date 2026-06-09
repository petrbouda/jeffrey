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
 * Structured pieces of an entity name, styled consistently by {@link MetricName.vue} but parsed
 * per use-case. The parsing differs (span tags vs HTTP URIs vs gRPC FQNs vs JDBC groups); the visual
 * vocabulary does not.
 *
 * - `group`   — leading category token, highlighted distinctly (e.g. `http`, `profile`)
 * - `name`    — ordinary name text, no special emphasis (the remainder after the group)
 * - `path`    — dimmed connective text (namespace, package)
 * - `segment` — an emphasised static URI segment (bold italic)
 * - `sep`     — a grey separator such as the URI `/`
 * - `var`     — a highlighted variable (HTTP path param like `{id}`)
 * - `leaf`    — the final, most specific segment, emphasised in bold
 */
export type NameSegmentKind = 'group' | 'name' | 'path' | 'segment' | 'sep' | 'var' | 'leaf';

export interface NameSegment {
  kind: NameSegmentKind;
  text: string;
}

/**
 * Dot-notation name where only the leading group is highlighted: the first token before the first
 * dot is the `group`, everything from that dot onward is ordinary `name` text. Used by Span Tags
 * (`http.RecordingsController.analyzeRecording` → **http**.RecordingsController.analyzeRecording) and
 * JDBC groups (free dot notation). Names without a dot render plain, with no group.
 */
export function parseGroupedName(name: string, fallback = '(none)'): NameSegment[] {
  if (!name) {
    return [{ kind: 'name', text: fallback }];
  }

  const firstDot = name.indexOf('.');
  if (firstDot <= 0) {
    // No dot (or leading dot) — nothing to group, render it all as plain name text.
    return [{ kind: 'name', text: name }];
  }

  return [
    { kind: 'group', text: name.slice(0, firstDot) },
    { kind: 'name', text: name.slice(firstDot) }
  ];
}

/**
 * HTTP URI: `/`-separated. Static segments are emphasised (`segment`, bold italic), `{param}` segments
 * are highlighted (`var`, purple italic), and the slashes are grey `sep` — so the path structure and
 * the variables both read clearly.
 */
export function parseUriName(uri: string): NameSegment[] {
  if (!uri) {
    return [{ kind: 'sep', text: '/' }];
  }

  const parts = uri.split('/').filter(part => part.length > 0);
  if (parts.length === 0) {
    return [{ kind: 'sep', text: '/' }];
  }

  const segments: NameSegment[] = [];
  parts.forEach(part => {
    segments.push({ kind: 'sep', text: '/' });
    const isVariable = part.startsWith('{') && part.endsWith('}');
    segments.push({ kind: isVariable ? 'var' : 'segment', text: part });
  });
  return segments;
}

/**
 * Fully-qualified name split on the LAST dot: everything up to and including it is dimmed `path`
 * (the package), the simple name is the `leaf`. Used by gRPC services.
 */
export function parseQualifiedName(name: string): NameSegment[] {
  if (!name) {
    return [{ kind: 'leaf', text: '' }];
  }
  const lastDot = name.lastIndexOf('.');
  if (lastDot < 0) {
    return [{ kind: 'leaf', text: name }];
  }
  return [
    { kind: 'path', text: name.slice(0, lastDot + 1) },
    { kind: 'leaf', text: name.slice(lastDot + 1) }
  ];
}
