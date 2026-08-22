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
 * Free-form operational messages an application writes into its own recording, so that "what the
 * process thought was happening" sits next to the samples and events that show what actually did.
 * <ul>
 *   <li>{@link cafe.jeffrey.jfr.events.message.MessageEvent} ({@code jeffrey.Message}) — a
 *       lifecycle or monitoring note: a cache warmed, a feature flag flipped, a threshold
 *       crossed</li>
 *   <li>{@link cafe.jeffrey.jfr.events.message.AlertEvent} ({@code jeffrey.Alert}) — a condition
 *       that requires attention; same shape, stronger claim</li>
 * </ul>
 * Both carry a stable {@code type} (an identifier such as {@code CONNECTION_POOL_EXHAUSTED} —
 * screaming snake case, one per kind of message, never per occurrence), a short {@code title}, a
 * detailed {@code message}, a {@code severity} (the name of a
 * {@link cafe.jeffrey.jfr.events.message.Severity} constant), a {@code category} and the
 * {@code source} component that raised it.
 *
 * <pre>{@code
 * AlertEvent alert = new AlertEvent();
 * alert.type = "EVENT_PROCESSING_FAILED";
 * alert.title = "Recording ingestion failed";
 * alert.message = "Chunk 42 of profile.jfr could not be parsed: " + e.getMessage();
 * alert.severity = Severity.HIGH.name();
 * alert.category = "AVAILABILITY";
 * alert.source = "recording-ingestion";
 * alert.commit();
 * }</pre>
 *
 * These are instants, not spans: they mark a moment, carry no duration of their own, and do not
 * take part in traces.
 */
package cafe.jeffrey.jfr.events.message;
