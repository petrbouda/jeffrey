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

package cafe.jeffrey.jfr.events.jdbc.pool;

import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Period;

@Name(JdbcPoolStatisticsEvent.NAME)
@Label("Pool Statistics")
@Period("1 s")
@Description("Statistics of the connection pool")
public class JdbcPoolStatisticsEvent extends JdbcPoolEvent {

    public static final String NAME = "jeffrey.JdbcPoolStatistics";

    @Label("Total Connections")
    public int total;

    @Label("Idle Connections")
    public int idle;

    @Label("Active Connections")
    public int active;

    @Label("Max Connections")
    public int max;

    @Label("Min Connections")
    public int min;

    @Label("Pending Threads")
    public int pendingThreads;
}
