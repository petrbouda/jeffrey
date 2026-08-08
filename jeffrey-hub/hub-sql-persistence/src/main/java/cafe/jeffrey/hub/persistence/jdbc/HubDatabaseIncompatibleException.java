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

package cafe.jeffrey.hub.persistence.jdbc;

/**
 * The existing hub database was created by an older Jeffrey version and cannot be used by this
 * build. Surfaced at startup as an actionable console error by the hub's failure analyzer.
 */
public class HubDatabaseIncompatibleException extends RuntimeException {

    private final String databaseUrl;

    public HubDatabaseIncompatibleException(String databaseUrl, String detail) {
        super("Hub database is incompatible with this Jeffrey version: database_url=%s detail=%s"
                .formatted(databaseUrl, detail));
        this.databaseUrl = databaseUrl;
    }

    public String databaseUrl() {
        return databaseUrl;
    }
}
