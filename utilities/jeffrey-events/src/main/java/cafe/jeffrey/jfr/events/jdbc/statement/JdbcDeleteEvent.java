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

@Name(JdbcDeleteEvent.NAME)
@Label("JDBC Delete Statement")
@Category({"Application", "JDBC"})
public class JdbcDeleteEvent extends JdbcBaseEvent {

    public static final String NAME = "jeffrey.JdbcDelete";

    public JdbcDeleteEvent(String name, String group) {
        super(name, group);
    }
}
