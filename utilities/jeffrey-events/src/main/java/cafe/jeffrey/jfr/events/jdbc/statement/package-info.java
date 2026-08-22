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
 * JDBC statement events: one event per statement run against a database, split by verb so the
 * Database dashboard can aggregate reads and writes separately.
 * <ul>
 *   <li>{@link cafe.jeffrey.jfr.events.jdbc.statement.JdbcQueryEvent} ({@code jeffrey.JdbcQuery})
 *       — SELECT</li>
 *   <li>{@link cafe.jeffrey.jfr.events.jdbc.statement.JdbcInsertEvent} ({@code jeffrey.JdbcInsert})
 *       — INSERT, with {@code isLob}/{@code isBatch} markers</li>
 *   <li>{@link cafe.jeffrey.jfr.events.jdbc.statement.JdbcUpdateEvent} ({@code jeffrey.JdbcUpdate})
 *       — UPDATE</li>
 *   <li>{@link cafe.jeffrey.jfr.events.jdbc.statement.JdbcDeleteEvent} ({@code jeffrey.JdbcDelete})
 *       — DELETE</li>
 *   <li>{@link cafe.jeffrey.jfr.events.jdbc.statement.JdbcExecuteEvent} ({@code
 *       jeffrey.JdbcExecute}) — DDL and anything else</li>
 *   <li>{@link cafe.jeffrey.jfr.events.jdbc.statement.JdbcStreamEvent} ({@code jeffrey.JdbcStream})
 *       — a query consumed as a stream, committed when the stream closes</li>
 * </ul>
 * Every statement event is a <b>leaf span</b> of kind {@code CLIENT}: the constructor takes the
 * statement's {@code name} (its label — a mapper method id, a named query — never SQL text) and a
 * {@code group} the dashboard clusters statements under.
 *
 * <h2>The emit shape</h2>
 * Committed in its own {@code finally} through
 * {@link cafe.jeffrey.jfr.events.trace.AbstractTracedEvent#commitSpan() commitSpan()}, the event
 * nests under whatever span is in progress on the executing thread — usually the request being
 * served — or records untraced-but-present when there is none.
 *
 * <pre>{@code
 * JdbcQueryEvent event = new JdbcQueryEvent("UserMapper.selectById", "UserMapper");
 * if (!event.isEnabled()) {
 *     return doQuery();
 * }
 * event.begin();
 * List<User> rows = null;
 * try {
 *     rows = doQuery();
 *     event.end();                    // interval ends when the work ends
 *     return rows;
 * } catch (Exception e) {
 *     event.failed(e);                // status=ERROR + errorType; the span shows red
 *     throw e;
 * } finally {
 *     if (event.shouldCommit()) {     // respects per-recording thresholds
 *         event.sql = sql;            // fill fields only when actually committing
 *         event.rows = rows != null ? rows.size() : 0;
 *         event.commitSpan();         // stamps as a child of the span in progress
 *     }
 * }
 * }</pre>
 *
 * On the exception path {@code end()} is deliberately not called — {@code commit()} closes the
 * interval itself.
 *
 * <h2>Deferred commits</h2>
 * A {@link cafe.jeffrey.jfr.events.jdbc.statement.JdbcStreamEvent} commits from the stream's
 * {@code close()}, which may run after the enclosing span's binding is gone — or inside someone
 * else's. Such an emitter stamps eagerly with
 * {@link cafe.jeffrey.jfr.events.trace.Tracer#stamp Tracer.stamp} at construction;
 * {@code commitSpan()} never re-stamps an event that already carries identity.
 *
 * <h2>Correctness notes</h2>
 * <ul>
 *   <li>{@code sql} with {@code ?} placeholders is <em>good</em>: identical statements aggregate.
 *       Never inline parameter values into {@code sql} — and never into the <em>name</em>.</li>
 *   <li>Parameter values, if recorded at all, go into {@code params} as a JSON object string —
 *       scrub anything sensitive; the recording contains it verbatim.</li>
 *   <li>For large batches set {@code isBatch} and skip {@code sql}/{@code params}.</li>
 * </ul>
 */
package cafe.jeffrey.jfr.events.jdbc.statement;
