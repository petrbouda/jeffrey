/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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
 * Severity levels matching cafe.jeffrey.jfr.events.message.Severity
 */
export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

/**
 * ImportantMessage interface matching ImportantMessageResponse from the API.
 */
export interface ImportantMessage {
  /** Identifier for this type of message (e.g., HIGH_CPU_USAGE, CONNECTION_POOL_EXHAUSTED) */
  type: string;
  /** Short summary of the message */
  title: string;
  /** Detailed description of the message */
  message: string;
  /** The severity level of the message */
  severity: Severity;
  /** The category of the message (e.g., PERFORMANCE, SECURITY, RESOURCE, AVAILABILITY) */
  category: string;
  /** The component or service that raised the message */
  source: string;
  /** The message is intended to be processed as an alert */
  isAlert: boolean;
  /** Session ID this message belongs to */
  sessionId: string;
  /** ISO timestamp when the message was created */
  createdAt: string;
}
