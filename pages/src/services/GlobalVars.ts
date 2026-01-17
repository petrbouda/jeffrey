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

export default class GlobalVars {
    static internalUrl = (() => {
        // In production, frontend is served by backend on same port, use relative URL
        // In development (Vite dev server), frontend runs on different port, use absolute URL
        if (import.meta.env.DEV) {
            return 'http://localhost:8080/api/internal';
        } else {
            return '/api/internal';
        }
    })()

    static SAP_EVENT_LINK = 'https://sap.github.io/SapMachine/jfrevents/23.html'
}
