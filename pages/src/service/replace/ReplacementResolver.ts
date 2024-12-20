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
import CompressionUtils from "@/service/CompressionUtils";
import ReplaceableToken from "@/service/replace/ReplaceableToken";
import Utils from "@/service/Utils";

export default class ReplacementResolver {

    // Hack to figure out whether the value was changed
    static REPLACED_SEARCH_CHECK = "{{@REPLACE_SEARCH}}"
    static REPLACED_SECONDARY_SUBSECOND = "{{@REPLACE_SUBSECOND_SECONDARY}}"

    static useWeight(): boolean {
        return Utils.parseBoolean(ReplaceableToken.FLAMEGRAPH_USE_WEIGHT)
    }

    static search(): string | null {
        if (this.REPLACED_SEARCH_CHECK.replace("@", "") !== ReplaceableToken.FLAMEGRAPH_SEARCH) {
            return ReplaceableToken.FLAMEGRAPH_SEARCH
        }
        return null
    }

    static withTimeseries(): boolean {
        return Utils.parseBoolean(ReplaceableToken.FLAMEGRAPH_WITH_TIMESERIES)
    }

    static eventType(): string {
        return ReplaceableToken.FLAMEGRAPH_EVENT_TYPE
    }

    static graphType(): string {
        return ReplaceableToken.GRAPH_TYPE
    }

    static flamegraphData(): string {
        return CompressionUtils.decodeAndDecompress(ReplaceableToken.FLAMEGRAPH)
    }

    static timeseriesData(): string {
        return CompressionUtils.decodeAndDecompress(ReplaceableToken.TIMESERIES)
    }

    static primarySubSecond(): string {
        return CompressionUtils.decodeAndDecompress(ReplaceableToken.SUBSECOND_PRIMARY)
    }

    static secondarySubSecond(): string | null {
        if (this.REPLACED_SECONDARY_SUBSECOND.replace("@", "") !== ReplaceableToken.SUBSECOND_SECONDARY) {
            return CompressionUtils.decodeAndDecompress(ReplaceableToken.SUBSECOND_SECONDARY)
        }
        return null
    }

    static resolveSubSecondCommand(): string {
        return ReplaceableToken.SUBSECOND_COMMAND
    }
}
