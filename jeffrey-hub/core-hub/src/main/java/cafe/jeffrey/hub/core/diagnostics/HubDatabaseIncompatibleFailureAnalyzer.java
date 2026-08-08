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
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns a {@link HubDatabaseIncompatibleException} thrown during startup into a clear,
 * actionable console error instead of a raw stack trace. The hub is a server deployment,
 * so unlike the microscope there is no web recovery mode — the operator fixes it manually
 * with one of the two suggested actions.
 */
public class HubDatabaseIncompatibleFailureAnalyzer
        extends AbstractFailureAnalyzer<HubDatabaseIncompatibleException> {

    private static final String DESCRIPTION = """
            Jeffrey Hub cannot start: the data folder was created by an older version.

            Jeffrey was updated and this version cannot use the existing database:

              Database : %s

            Affected data: hub workspaces, projects and scheduling.""";

    private static final String ACTION = """
            Choose one of the following and start Jeffrey Hub again:

              1. Start fresh          remove the hub home folder (default: ~/.jeffrey-hub)
              2. Use another folder   start with --jeffrey.hub.home.dir=/path/to/new/home""";

    @Override
    protected FailureAnalysis analyze(Throwable rootFailure, HubDatabaseIncompatibleException cause) {
        return new FailureAnalysis(DESCRIPTION.formatted(cause.databaseUrl()), ACTION, cause);
    }
}
