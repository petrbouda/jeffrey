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
 * A typed value from a JFR event field. Exactly one of the value properties will be set.
 * Annotations are resolved: @Timestamp -> epoch millis, @Timespan -> nanoseconds, @Percentage -> float.
 */
export interface TypedValue {
  stringValue?: string;
  longValue?: number;
  doubleValue?: number;
  boolValue?: boolean;
  floatValue?: number;
}

/**
 * A single JFR event received from the streaming subscription.
 */
export interface StreamingEvent {
  eventType: string;
  sessionId: string;
  timestamp: number;
  fields: Record<string, TypedValue>;
}
