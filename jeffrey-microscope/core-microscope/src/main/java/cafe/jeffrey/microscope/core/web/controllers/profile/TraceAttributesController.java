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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.TraceAttributesManager;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeKeyRow;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeLatency;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeSearchResult;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeTimelineBucket;
import cafe.jeffrey.profile.manager.model.trace.TraceAttributeValues;
import cafe.jeffrey.profile.manager.model.trace.TraceEventTypeRow;
import cafe.jeffrey.provider.profile.api.TraceAttributeCondition;
import cafe.jeffrey.provider.profile.api.TraceAttributeKeyId;
import cafe.jeffrey.provider.profile.api.TraceAttributeLatencyQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeOperator;
import cafe.jeffrey.provider.profile.api.TraceAttributeScope;
import cafe.jeffrey.provider.profile.api.TraceAttributeSearchQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeSource;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueQuery;
import cafe.jeffrey.provider.profile.api.TraceAttributeValueSortField;
import cafe.jeffrey.provider.profile.api.TraceSortField;
import cafe.jeffrey.shared.common.exception.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves the Traces by Attributes views: the event types whose carriers can be searched and the keys each of
 * them carried, a search over what spans recorded, and one key's values broken down and distributed.
 * <p>
 * Sits alongside {@link TracesController} rather than inside it: every path here has a literal first
 * segment, so none of them can be mistaken for that controller's {@code /{traceId}} template, and
 * the two features stay independently readable.
 * <p>
 * Conditions travel in the query string so that a filter is a link — "look at the failed traces of
 * this tenant" is something to paste into an issue rather than a set of instructions to follow.
 */
@RestController
@RequestMapping("/api/internal/profiles/{profileId}/traces/attributes")
public class TraceAttributesController {

    private static final Logger LOG = LoggerFactory.getLogger(TraceAttributesController.class);

    /**
     * What separates the fields of one condition in the query string.
     * <p>
     * A tilde rather than a colon: the values being matched are routinely URIs and SQL, and both are
     * full of colons. The value is parsed last and keeps any separator it contains, so only a key
     * containing a tilde is unrepresentable — which no recording has produced.
     */
    private static final String CONDITION_SEPARATOR = "~";

    /** {@code source~owner~key~operator~value} — five fields, the last of which may contain more. */
    private static final int CONDITION_FIELDS = 5;

    private static final String DEFAULT_SEARCH_LIMIT = "50";
    private static final String DEFAULT_VALUES_LIMIT = "50";
    /** Enough rows to see a distribution's shape, few enough that the grid stays readable. */
    private static final String DEFAULT_LATENCY_VALUES = "12";
    private static final int MAX_LATENCY_VALUES = 40;
    private static final int MAX_LIMIT = 10_000;
    /** The same strip width the trace list's own density bar uses, so the two read alike. */
    private static final String DEFAULT_TIMELINE_BUCKETS = "60";
    private static final int MAX_TIMELINE_BUCKETS = 500;

    private static final String DEFAULT_SEARCH_SORT = "DURATION";
    private static final String DEFAULT_SCOPE = "TRACE";
    /** Total time, not call count: a busy value and an expensive value are rarely the same value. */
    private static final String DEFAULT_VALUES_SORT = "TOTAL_TIME";

    private final ProfileManagerResolver resolver;

    public TraceAttributesController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Every key the profile recorded. The list the rail renders, and the only call that has to
     * happen before anything can be asked about a key.
     */
    @GetMapping("/keys")
    public List<TraceAttributeKeyRow> keys(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "eventType", required = false) String eventType) {

        LOG.debug("Listing trace attribute keys: profile_id={} event_type={}", profileId, eventType);

        // Scoped to an event type this is the picker's second step, where the counts are that type's
        // own; unscoped it is the whole catalog, which is what the search builder offers.
        return eventType == null || eventType.isBlank()
                ? manager(profileId).keys()
                : manager(profileId).keysOf(eventType);
    }

    /**
     * The event types whose carriers can be searched — the attribute picker's first step.
     * <p>
     * A recording carries hundreds of JFR event types; these are the ones that opened spans, which
     * is what makes choosing one a reasonable first move rather than a search through a catalog.
     */
    @GetMapping("/event-types")
    public List<TraceEventTypeRow> eventTypes(@PathVariable("profileId") String profileId) {
        LOG.debug("Listing span-producing event types: profile_id={}", profileId);
        return manager(profileId).attributeEventTypes();
    }

    /**
     * The traces whose spans satisfy every condition.
     * <p>
     * With no condition this is the unfiltered trace list, which is what the page opens with — the
     * same rows the trace list itself would show, so the two never disagree about what is in the
     * profile.
     */
    @GetMapping("/search")
    public TraceAttributeSearchResult search(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "where", required = false) List<String> where,
            @RequestParam(value = "scope", defaultValue = DEFAULT_SCOPE) TraceAttributeScope scope,
            @RequestParam(value = "sort", defaultValue = DEFAULT_SEARCH_SORT) TraceSortField sort,
            @RequestParam(value = "desc", defaultValue = "true") boolean descending,
            @RequestParam(value = "limit", defaultValue = DEFAULT_SEARCH_LIMIT) int limit,
            @RequestParam(value = "offset", defaultValue = "0") int offset) {

        LOG.debug("Searching traces by attributes: profile_id={} conditions={} scope={} sort={} limit={}",
                profileId, where == null ? 0 : where.size(), scope, sort, limit);

        return manager(profileId).search(new TraceAttributeSearchQuery(
                conditions(where), scope, sort, descending, boundedLimit(limit), boundedOffset(offset)));
    }

    /**
     * Where in the recording the matches fell, against every trace as a backdrop.
     */
    @GetMapping("/timeline")
    public List<TraceAttributeTimelineBucket> timeline(
            @PathVariable("profileId") String profileId,
            @RequestParam(value = "where", required = false) List<String> where,
            @RequestParam(value = "scope", defaultValue = DEFAULT_SCOPE) TraceAttributeScope scope,
            @RequestParam(value = "buckets", defaultValue = DEFAULT_TIMELINE_BUCKETS) int buckets) {

        LOG.debug("Bucketing attribute matches: profile_id={} conditions={} buckets={}",
                profileId, where == null ? 0 : where.size(), buckets);

        // The sort and paging carry no meaning for a bucketed count; the query record still needs
        // them, so it gets the list's own defaults rather than a second, half-empty query type.
        TraceAttributeSearchQuery query = new TraceAttributeSearchQuery(
                conditions(where), scope, TraceSortField.DURATION, true, 1, 0);

        return manager(profileId).timeline(query, Math.clamp(buckets, 1, MAX_TIMELINE_BUCKETS));
    }

    /**
     * One key's values, ranked, with what each cost.
     */
    @GetMapping("/values")
    public TraceAttributeValues values(
            @PathVariable("profileId") String profileId,
            @RequestParam("source") TraceAttributeSource source,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam("key") String key,
            @RequestParam(value = "sort", defaultValue = DEFAULT_VALUES_SORT) TraceAttributeValueSortField sort,
            @RequestParam(value = "desc", defaultValue = "true") boolean descending,
            @RequestParam(value = "limit", defaultValue = DEFAULT_VALUES_LIMIT) int limit,
            @RequestParam(value = "eventType", required = false) String eventType) {

        LOG.debug("Breaking down attribute key: profile_id={} source={} owner={} key={} event_type={} sort={}",
                profileId, source, owner, key, eventType, sort);

        return manager(profileId).values(new TraceAttributeValueQuery(
                keyId(source, owner, key), sort, descending, boundedLimit(limit), scope(eventType)));
    }

    /**
     * How each of a key's values is distributed over trace duration.
     */
    @GetMapping("/latency")
    public TraceAttributeLatency latency(
            @PathVariable("profileId") String profileId,
            @RequestParam("source") TraceAttributeSource source,
            @RequestParam(value = "owner", required = false) String owner,
            @RequestParam("key") String key,
            @RequestParam(value = "maxValues", defaultValue = DEFAULT_LATENCY_VALUES) int maxValues,
            @RequestParam(value = "eventType", required = false) String eventType) {

        LOG.debug("Distributing attribute key over duration: profile_id={} key={} event_type={} max_values={}",
                profileId, key, eventType, maxValues);

        return manager(profileId).latency(new TraceAttributeLatencyQuery(
                keyId(source, owner, key),
                Math.clamp(maxValues, 1, MAX_LATENCY_VALUES),
                scope(eventType)));
    }

    /**
     * The event type a breakdown is scoped to, or null for the whole profile. A blank parameter is
     * the same as none: it can only arrive that way from a URL that carried the key but not the type.
     */
    private static String scope(String eventType) {
        return eventType == null || eventType.isBlank() ? null : eventType;
    }

    /**
     * Parses the conditions out of the query string, rejecting a malformed one rather than dropping
     * it: a filter silently narrowed to fewer conditions than were asked for returns more traces
     * than it should, and looks like it worked.
     */
    private static List<TraceAttributeCondition> conditions(List<String> where) {
        if (where == null || where.isEmpty()) {
            return List.of();
        }
        return where.stream()
                .map(TraceAttributesController::condition)
                .toList();
    }

    private static TraceAttributeCondition condition(String encoded) {
        String[] fields = encoded.split(CONDITION_SEPARATOR, CONDITION_FIELDS);
        if (fields.length < CONDITION_FIELDS - 1) {
            throw Exceptions.invalidRequest("Malformed condition: " + encoded);
        }

        try {
            TraceAttributeSource source = TraceAttributeSource.valueOf(fields[0]);
            String owner = fields[1].isBlank() ? null : fields[1];
            TraceAttributeOperator operator = TraceAttributeOperator.valueOf(fields[3]);
            // EXISTS takes no value, so a condition using it is one field short by design.
            String value = fields.length == CONDITION_FIELDS ? fields[4] : null;

            return new TraceAttributeCondition(keyId(source, owner, fields[2]), operator, value);
        } catch (IllegalArgumentException e) {
            throw Exceptions.invalidRequest("Malformed condition: " + encoded + " — " + e.getMessage());
        }
    }

    private static TraceAttributeKeyId keyId(TraceAttributeSource source, String owner, String key) {
        return new TraceAttributeKeyId(source, owner == null || owner.isBlank() ? null : owner, key);
    }

    private TraceAttributesManager manager(String profileId) {
        return resolver.resolve(profileId).traceAttributesManager();
    }

    private static int boundedLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private static int boundedOffset(int offset) {
        return Math.clamp(offset, 0, MAX_LIMIT);
    }
}
