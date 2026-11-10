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
 * Escaping for the places that build markup as a string.
 *
 * Tooltips and other canvas overlays cannot use Vue's own interpolation — they hand a string to
 * `innerHTML` — and much of what they render comes out of a recording: remote hosts, file paths,
 * class names, thread names. None of that is under our control, so it is escaped here rather than
 * trusted at each call site.
 */

/**
 * Escapes text going into element content.
 */
export function escapeHtml(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/**
 * Escapes text going into a double-quoted attribute value, where a bare quote would end the
 * attribute early.
 */
export function escapeAttr(value: string): string {
  return escapeHtml(value).replace(/"/g, '&quot;');
}
