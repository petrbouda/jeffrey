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
import jdk.jfr.Timespan;

@Name(PooledJdbcConnectionCreatedEvent.NAME)
@Label("Connection Created")
@Description("Duration of creating a new connection in the pool")
public class PooledJdbcConnectionCreatedEvent extends JdbcPoolEvent {

    public static final String NAME = "jeffrey.PooledJdbcConnectionCreated";

    @Label("Creation Time")
    @Timespan()
    public long elapsedTime;
}
