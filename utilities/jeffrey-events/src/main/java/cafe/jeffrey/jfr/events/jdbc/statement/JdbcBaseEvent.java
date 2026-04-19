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

package cafe.jeffrey.jfr.events.jdbc.statement;

import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Label;

public abstract class JdbcBaseEvent extends Event {

    @Label("SQL Query")
    @Description("The SQL statement executed by the JDBC statement")
    public String sql;

    @Label("SQL Parameters")
    public String params;

    @Label("Statement Name")
    public String name;

    @Label("Label for Statement Grouping")
    public String group;

    @Label("Affected/Returned Rows")
    @Description("The number of affected/returned rows")
    public long rows;

    @Label("Successful Execution")
    @Description("SQL Statement ended up successfully")
    public boolean isSuccess = true;

    public JdbcBaseEvent(String name, String group) {
        this.name = name;
        this.group = group;
    }
}
