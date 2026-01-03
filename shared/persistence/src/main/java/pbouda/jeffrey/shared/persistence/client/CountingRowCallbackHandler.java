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

package pbouda.jeffrey.shared.persistence.client;

import org.springframework.jdbc.core.RowCallbackHandler;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.LongAdder;

public class CountingRowCallbackHandler implements RowCallbackHandler {

    private final LongAdder counter = new LongAdder();

    private final RowCallbackHandler handler;

    public CountingRowCallbackHandler(RowCallbackHandler handler) {
        this.handler = handler;
    }

    @Override
    public void processRow(ResultSet rs) throws SQLException {
        counter.increment();
        handler.processRow(rs);
    }

    public long getRowCount() {
        return counter.longValue();
    }
}
