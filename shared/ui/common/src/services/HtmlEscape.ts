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
