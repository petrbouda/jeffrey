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

package cafe.jeffrey.jfr.events.jdbc.statement;

import jdk.jfr.Category;
import jdk.jfr.Label;
import jdk.jfr.Name;

/**
 * A query consumed as a stream, committed when the stream closes rather than when the statement
 * returns. That deferred commit may run after the enclosing span's binding is gone — or inside
 * someone else's — so the emitter stamps eagerly with
 * {@link cafe.jeffrey.jfr.events.trace.Tracer#stamp Tracer.stamp} at construction;
 * {@link cafe.jeffrey.jfr.events.trace.AbstractTracedEvent#commitSpan() commitSpan()} never
 * re-stamps an event that already carries identity.
 */
@Name(JdbcStreamEvent.NAME)
@Label("JDBC Stream Statement")
@Category({"Application", "JDBC"})
public class JdbcStreamEvent extends JdbcQueryEvent {

    public static final String NAME = "jeffrey.JdbcStream";

    public JdbcStreamEvent(String name, String group) {
        super(name, group);
    }
}
