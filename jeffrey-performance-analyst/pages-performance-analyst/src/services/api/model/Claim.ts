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
 * One citation from a recommendation, after Jeffrey checked it against the measured profile and the
 * analyzed checkout. `selfPct` and `totalPct` are only meaningful when `grounded` is true — an
 * ungrounded claim names a frame that was never sampled, so it has no measured share at all.
 */
export default interface Claim {
  title: string;
  citedFrame: string;
  sourcePath: string | null;
  grounded: boolean;
  sourceFound: boolean;
  selfPct: number;
  totalPct: number;
}
