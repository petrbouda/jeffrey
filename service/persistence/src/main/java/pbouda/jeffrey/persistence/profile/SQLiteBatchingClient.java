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

package pbouda.jeffrey.persistence.profile;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteBatchingClient extends SQLiteClient {

    public SQLiteBatchingClient(DataSource dataSource, String preparedQuery) {
        super(dataSource, preparedQuery);
    }

    public void addBatch() {
        retry(statement::addBatch);
    }

    public void executeAndClearBatch() {
        retry(statement::executeBatch);

        try {
            statement.clearBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Cannot clear a batch", e);
        }
    }

    /**
     * <a href="https://www.sqlite.org/pragma.html#pragma_wal_autocheckpoint">PRAGMA WAL auto-checkpoint</a>
     * </br>
     * Disables auto-checkpoint for write-ahead log. The checkpoints are triggered when N-number of pages are written
     * into the WAL file. The default value is 1000 pages. The checkpoint is a process that writes the data from the
     * WAL file into the main database file. This disables the automatic checkpointing, and we need to ensure to call
     * checkpoint manually: {@link #flushDatabaseWriteAheadLog()}.
     */
    public void disableCheckpointingInWriteAheadLog() {
        try (Statement stmt = connection.createStatement()) {
            retry(() -> stmt.execute("PRAGMA wal_autocheckpoint=-1;"));
        } catch (SQLException e) {
            throw new RuntimeException("Cannot disable WAL checkpoint", e);
        }
    }

    /**
     * <a href="https://www.sqlite.org/pragma.html#pragma_wal_checkpoint">PRAGMA WAL checkpoint</a>
     * </br>
     * Triggers a checkpoint in the write-ahead log. The checkpoint writes the data from the WAL file into the main
     * database file.
     */
    public void flushDatabaseWriteAheadLog() {
        try (Statement stmt = connection.createStatement()) {
            retry(() -> stmt.execute("PRAGMA wal_checkpoint(FULL)"));
        } catch (SQLException e) {
            throw new RuntimeException("Cannot disable WAL checkpoint", e);
        }
    }

}
