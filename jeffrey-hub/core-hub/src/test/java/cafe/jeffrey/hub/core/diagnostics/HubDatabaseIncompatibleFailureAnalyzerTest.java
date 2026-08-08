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

package cafe.jeffrey.hub.core.diagnostics;

import cafe.jeffrey.hub.persistence.jdbc.HubDatabaseIncompatibleException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.diagnostics.FailureAnalysis;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HubDatabaseIncompatibleFailureAnalyzerTest {

    private static final String DATABASE_URL = "jdbc:duckdb:/home/tester/.jeffrey-hub/jeffrey-data.db";

    @Test
    void producesActionableAnalysisWithoutImplementationDetails() {
        HubDatabaseIncompatibleException cause =
                new HubDatabaseIncompatibleException(DATABASE_URL, "checksum mismatch V001__init.sql");

        FailureAnalysis analysis = new HubDatabaseIncompatibleFailureAnalyzer().analyze(cause, cause);

        assertNotNull(analysis);
        assertTrue(analysis.getDescription().contains(DATABASE_URL));
        assertTrue(analysis.getDescription().contains("older version"));
        assertTrue(analysis.getAction().contains("--jeffrey.hub.home.dir="));
        assertTrue(analysis.getAction().contains(".jeffrey-hub"));
    }
}
