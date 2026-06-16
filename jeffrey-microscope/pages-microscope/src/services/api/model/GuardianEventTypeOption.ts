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

/** Origin of a suggested event type, used to group the editor's suggestions. */
export type GuardianEventTypeSource = 'JDK' | 'ASYNC_PROFILER';

/**
 * A stack-trace–carrying event type offered by the Guardian guard editor. The user may also type a
 * custom event type that is not present in this catalog.
 */
export default interface GuardianEventTypeOption {
  code: string;
  label: string;
  source: GuardianEventTypeSource;
}
