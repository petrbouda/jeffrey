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

package cafe.jeffrey.jfr.events.servlet;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Collection;

/**
 * Attaches the application's own detail to an inbound request's span — <em>which tenant was this</em>,
 * <em>which API version</em>, <em>which client</em>.
 * <p>
 * The same bargain {@link HttpRequestNaming} strikes, for a different question: the filter asks,
 * and whoever knows the application answers. An exchange event declares what HTTP itself can say
 * (method, URI template, status, lengths); everything past that is domain knowledge the servlet
 * layer has no way to name, so it is contributed from outside.
 * <p>
 * What is contributed here lands in the span's open attribute map, and that choice is the point:
 * Jeffrey indexes that map <em>one key at a time</em>, so every key becomes something the Traces
 * attribute search can filter by, facet and rank — where a declared event field would have been one
 * opaque value. Keep the values low-cardinality for the same reason span names are kept low-cardinality:
 * a key past a couple of hundred distinct values stops being a browsable facet and becomes
 * search-only, and every distinct value enters the recording's constant pool. Capture what you
 * group by, not an identifier unique to each request.
 * <p>
 * On Spring, every bean of this type is collected and applied in {@code @Order} order. A customizer
 * that throws is logged and skipped — it can neither fail the request nor lose the span.
 */
@FunctionalInterface
public interface HttpExchangeAttributesCustomizer {

    /**
     * Contributes whatever this exchange should be searchable by.
     * <p>
     * Called once per recorded exchange, after the response is complete, so the response is safe to
     * read here — its status and headers are final.
     */
    void customize(HttpExchangeAttributes attributes, HttpServletRequest request, HttpServletResponse response);

    /**
     * Records the named request headers, each under the header's own name, lower-cased.
     * <p>
     * Names are matched case-insensitively, and a header sent more than once records its first
     * value and nothing else — an attribute is one scalar, and the attribute index would drop a
     * list.
     * <p>
     * An allow-list rather than a deny-list, and empty by default, because a recording is a file
     * that gets uploaded, shared and kept: a header is recorded because somebody named it, never
     * because nobody thought to exclude it.
     * <p>
     * There is deliberately no counterpart for response headers: a response header is one the
     * application's own server set, so reading it off the response this interface is handed is a
     * two-line customizer, where an inbound header may be touched by no application code at all.
     *
     * @param names the headers to record, e.g. {@code x-tenant-id}; never a credential-bearing one
     */
    static HttpExchangeAttributesCustomizer requestHeaders(Collection<String> names) {
        return new HeaderAttributesCustomizer(names);
    }
}
