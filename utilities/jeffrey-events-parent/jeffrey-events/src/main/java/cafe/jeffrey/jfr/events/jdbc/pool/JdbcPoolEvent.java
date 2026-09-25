/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.jfr.events.jdbc.pool;

import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.StackTrace;

/**
 * Anything the connection pool reports.
 * <p>
 * Deliberately not a traced event. The pool reports after the fact — an elapsed time handed to a
 * callback, or a periodic sample — with no interval of its own for JFR to time, so a pool event
 * turned into a span would draw as a zero-width bar in a waterfall while its own field claimed the
 * 200 ms it actually took. Whatever waited for the connection is the span; this stays a measurement
 * hanging off it.
 */
@Category({"Application", "JDBC Pool"})
@StackTrace(false)
public abstract class JdbcPoolEvent extends Event {

    @Label("Pool Name")
    public String poolName;
}
