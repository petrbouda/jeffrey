/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

// External tools from Java can directly replace these tokens to generate/export static graphs
// e.g. command-line tool from Java
export default class ReplaceableToken {

    static SUBSECOND_PRIMARY = "{{REPLACE_SUBSECOND_PRIMARY}}"
    static SUBSECOND_SECONDARY = "{{REPLACE_SUBSECOND_SECONDARY}}"
    static SUBSECOND_COMMAND = "{{REPLACE_SUBSECOND_COMMAND}}"

    static FLAMEGRAPH = "{{REPLACE_FLAMEGRAPH}}"
    static FLAMEGRAPH_SEARCH = "{{REPLACE_SEARCH}}"
    static FLAMEGRAPH_USE_WEIGHT = "{{REPLACE_USE_WEIGHT}}"
    static FLAMEGRAPH_EVENT_TYPE = "{{REPLACE_EVENT_TYPE}}"
    static FLAMEGRAPH_WITH_TIMESERIES = "{{REPLACE_WITH_TIMESERIES}}"

    static TIMESERIES = "{{REPLACE_TIMESERIES}}";

    // An external tool can propagate the information about Graph Type to the generated artifact
    static GRAPH_TYPE = "{{REPLACE_GRAPH_TYPE}}";
}
