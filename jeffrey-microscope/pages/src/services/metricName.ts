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

/**
 * A Java method reference, `Class#method` or `pkg.Class#method`: any package is dimmed `path`, the
 * class is ordinary `name` text and the method is the `leaf`. Used by traced-method spans, and
 * deliberately not the gRPC parse — a gRPC name bolds both halves because the service is a thing
 * you look up, whereas here the class is only where the method lives.
 *
 * A name with no `#` (the shape the derivation falls back to when the recording spells `method` in
 * a way it cannot split) renders as a plain qualified name rather than being forced into a split
 * that is not there.
 */
export function parseMethodName(name: string): NameSegment[] {
  if (!name) {
    return [{ kind: 'leaf', text: '' }];
  }

  const separator = name.indexOf('#');
  if (separator <= 0 || separator === name.length - 1) {
    return parseQualifiedName(name);
  }

  const owner = name.slice(0, separator);
  const method = name.slice(separator + 1);
  const lastDot = owner.lastIndexOf('.');

  const segments: NameSegment[] = [];
  if (lastDot >= 0) {
    segments.push({ kind: 'path', text: owner.slice(0, lastDot + 1) });
  }
  segments.push({ kind: 'name', text: owner.slice(lastDot + 1) });
  segments.push({ kind: 'sep', text: '#' });
  segments.push({ kind: 'leaf', text: method });
  return segments;
}
