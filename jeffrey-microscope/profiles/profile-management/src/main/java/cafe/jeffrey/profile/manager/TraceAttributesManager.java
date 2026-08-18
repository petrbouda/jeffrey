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

package cafe.jeffrey.profile.manager;

import cafe.jeffrey.profile.manager.model.trace.TraceAttributeDifferences;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeKeyRow;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeLatency;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeSearchResult;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeTimelineBucket;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeValues;
import cafe.jeffrey.provider.profile.api.TraceAttributeDifferenceQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyId;
import cafe.jeffrey.provider.profile.api.TraceAttributeSearchQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueQuery;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.util.List;
import java.util.function.Function;

/**
 * Reads the key/value payload of a profile's spans as dimensions the trace views can query, break
 * down and compare.
 * <p>
 * A span carries two of them — the open map a developer attached at the call site, and the fields
 * the event type declares about itself — and both were, until this existed, readable only one span
 * at a time inside a waterfall. Nothing here knows about any particular event type: a type
 * instrumented tomorrow is in the catalog the first time its recording is opened.
 */
public interface TraceAttributesManager {

    /**
     * How many values a key may have before it is treated as search-only.
     * <p>
     * The guard the whole feature leans on. A {@code user.id} attribute on sixty thousand spans is
     * legitimate instrumentation, and every breakdown of it — a facet list, a heatmap axis, a place
     * in the difference ranking — is eighteen thousand rows of one trace each, which is noise
     * wearing the shape of an answer.
     */
    long SEARCH_ONLY_ABOVE = 200;

    /** The most values any single breakdown returns, however wide the key is. */
    int MAX_VALUES = 100;

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, TraceAttributesManager> {
    }

    /**
     * Every key the profile recorded, grouped by where it came from and marked with whether it has
     * too many values to break down.
     */
    List<TraceAttributeKeyRow> keys();

    /**
     * The traces whose spans satisfy every condition, with the spans that satisfied them.
     */
    TraceAttributeSearchResult search(TraceAttributeSearchQuery query);

    /**
     * How the matched traces are spread over the recording, against every trace as a backdrop.
     *
     * @param buckets how many slices to divide the recording into
     */
    List<TraceAttributeTimelineBucket> timeline(TraceAttributeSearchQuery query, int buckets);

    /**
     * One key's values, ranked, with what each cost.
     * <p>
     * Ranked by total time by default rather than by how often a value appears, because a busy value
     * and an expensive value are rarely the same value and it is the expensive one being looked for.
     * The result says whether it is the whole set or only the top of it — a truncated list passed off
     * as complete is how a reader concludes a value does not exist.
     */
    TraceAttributeValues values(TraceAttributeValueQuery query);

    /**
     * The duration distribution of each of a key's values.
     *
     * @param maxValues how many of the key's values to cover, most-carried first
     */
    TraceAttributeLatency latency(TraceAttributeKeyId key, int maxValues);

    /**
     * Every key/value ranked by how much more common it is inside the selection than outside it —
     * the screen for when there are slow traces and no theory about them.
     */
    TraceAttributeDifferences differences(TraceAttributeDifferenceQuery query);
}
