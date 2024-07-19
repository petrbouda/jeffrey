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
import ReplaceableToken from "@/service/replace/ReplaceableToken";
import Utils from "@/service/Utils";

export default class ReplaceResolver {

    // Hack to figure out whether the value was changed
    static REPLACED_SEARCH_CHECK = "{{@REPLACE_SEARCH}}"

    static resolveWeight(generated, value) {
        if (generated) {
            return Utils.parseBoolean(ReplaceableToken.FLAMEGRAPH_USE_WEIGHT)
        }
        return Utils.parseBoolean(value);
    }

    static resolveSearch(generated) {
        if (generated && this.REPLACED_SEARCH_CHECK.replace("@", "") !== ReplaceableToken.FLAMEGRAPH_SEARCH) {
            return ReplaceableToken.FLAMEGRAPH_SEARCH
        }
        return null
    }

    static resolveWithTimeseries(generated, value) {
        if (generated) {
            return Utils.parseBoolean(ReplaceableToken.FLAMEGRAPH_WITH_TIMESERIES)
        }
        return Utils.parseBoolean(value)
    }

    static resolveEventType(generated, value) {
        if (generated) {
            return ReplaceableToken.FLAMEGRAPH_EVENT_TYPE
        }
        return value
    }

    static resolveGraphType(graphType, generated) {
        if (graphType == null && generated) {
            return ReplaceableToken.GRAPH_TYPE
        } else {
            return graphType
        }
    }

    static resolveSubSecondCommand() {
        return ReplaceableToken.SUBSECOND_COMMAND
    }
}
