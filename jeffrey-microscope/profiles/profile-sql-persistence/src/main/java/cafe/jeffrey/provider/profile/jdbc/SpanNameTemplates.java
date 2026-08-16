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

package cafe.jeffrey.provider.profile.jdbc;

import cafe.jeffrey.shared.common.model.EventTypeName;
import cafe.jeffrey.shared.common.model.SpanConventionKeys;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a span is named: one template per event type, in {@code @Span}'s syntax, rendered into
 * one SQL CASE expression. The template is the only naming concept — the built-ins for Jeffrey's
 * own exchange types are the same strings their annotations declare (pinned by
 * {@code JdbcTraceRepositoryTest.EventApiContract}), kept here for recordings made before the
 * annotation existed; what a recording declares for itself, carried in its metadata and stored by
 * the parser in {@code event_types.extras}, overlays them per event type and wins.
 * <p>
 * This is what makes naming generic: an event type Jeffrey has never seen annotates itself, and
 * the derivation names it with no change here — the same way span discovery finds it structurally
 * by its {@code spanId} field. Deriving the name from the event's own fields, rather than reading
 * back what one library version recorded, is what makes two recordings of the same endpoint one
 * operation.
 * <p>
 * Only the name is declarable. A span's verdict is the writer's statement — recorded through
 * {@code commitSpan()}/{@code failed()} — so there is deliberately nothing here that judges an
 * outcome; the built-in status rules for Jeffrey's own types live in {@link SpanConventions}.
 *
 * <h2>Trust boundary</h2>
 * Everything read here comes out of an arbitrary recording and ends up inside a SQL statement, so
 * nothing is interpolated unvalidated: a template token must match {@code [A-Za-z0-9_]+}, literal
 * text and event-type names have their quotes doubled. A template that fails any of this is logged
 * and dropped — the event type falls back to what it recorded — and {@code derive()} never fails
 * on it.
 */
final class SpanNameTemplates {

    private static final Logger LOG = LoggerFactory.getLogger(SpanNameTemplates.class);

    /**
     * The naming conventions for Jeffrey's own exchange types, as templates — the same strings the
     * {@code @Span} annotations on the event classes declare. New recordings carry these
     * declarations themselves; the map is what names the same events on recordings that predate
     * the annotation, and it is why that backward-compat set genuinely cannot grow.
     */
    static final Map<String, String> BUILT_IN = Map.of(
            EventTypeName.HTTP_SERVER_EXCHANGE, "{method} {uri}",
            EventTypeName.HTTP_CLIENT_EXCHANGE, "{method} {uri}",
            EventTypeName.GRPC_SERVER_EXCHANGE, "{service}/{method}",
            EventTypeName.GRPC_CLIENT_EXCHANGE, "{service}/{method}");

    /**
     * The template of a self-naming type: its name is the recorded {@code name} field, which is
     * exactly the fallback the name projection reads anyway. Declaring it keeps the invariant that
     * every shipped span type carries its convention; rendering it would generate CASE arms that
     * cannot change an answer, so it is skipped.
     */
    private static final String IDENTITY_TEMPLATE = "{name}";

    /** A template names fields as {@code {token}}; anything between tokens is literal text. */
    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    //language=SQL
    private static final String DECLARED_TEMPLATES = """
            SELECT
                name                                    AS event_type,
                json_extract_string(extras, '$.%s')     AS name_template
            FROM event_types
            WHERE json_extract_string(extras, '$.%s') IS NOT NULL
            """.formatted(SpanConventionKeys.EXTRAS_SPAN_NAME, SpanConventionKeys.EXTRAS_SPAN_NAME);

    /** One event type's declared template, as read from the profile — not yet validated. */
    private record Declaration(String eventType, String nameTemplate) {
    }

    private SpanNameTemplates() {
    }

    /**
     * The CASE expression naming every event type that has a template — built-in or declared in
     * the recording, declared winning per type. Rendered in event-type order so the derivation SQL
     * is deterministic. Never empty: the built-ins are always present.
     */
    static String nameCase(DatabaseClient databaseClient) {
        List<Declaration> declarations = databaseClient.query(
                StatementLabel.LOAD_SPAN_NAME_TEMPLATES,
                DECLARED_TEMPLATES,
                new MapSqlParameterSource(),
                (rs, _) -> new Declaration(rs.getString("event_type"), rs.getString("name_template")));

        Map<String, String> templates = new LinkedHashMap<>(BUILT_IN);
        for (Declaration declaration : declarations) {
            templates.put(declaration.eventType(), declaration.nameTemplate());
        }

        StringBuilder arms = new StringBuilder();
        for (Map.Entry<String, String> template : new TreeMap<>(templates).entrySet()) {
            String concatenation = renderTemplate(template.getKey(), template.getValue());
            if (concatenation != null) {
                arms.append("WHEN event_type = '%s' THEN %s\n".formatted(quoted(template.getKey()), concatenation));
            }
        }
        return "CASE %s END".formatted(arms);
    }

    /**
     * The template as a SQL concatenation: tokens become JSON reads of the event's own fields,
     * literal runs become quoted literals. {@code ||} propagates NULL, so an event missing a
     * templated field derives a NULL name and falls through — the same behaviour an exchange
     * without a URI always had.
     */
    private static String renderTemplate(String eventType, String template) {
        if (IDENTITY_TEMPLATE.equals(template)) {
            return null;
        }

        StringBuilder parts = new StringBuilder();
        Matcher tokens = TEMPLATE_TOKEN.matcher(template);
        int literalStart = 0;
        int tokenCount = 0;
        while (tokens.find()) {
            appendLiteral(parts, template.substring(literalStart, tokens.start()));
            appendPart(parts, "json_extract_string(fields, '$.%s')".formatted(tokens.group(1)));
            literalStart = tokens.end();
            tokenCount++;
        }
        appendLiteral(parts, template.substring(literalStart));

        // A template with no token names nothing of the event's — including one whose braces held
        // characters the token pattern refuses, which must not be quietly read as literal text.
        if (tokenCount == 0 || template.chars().filter(c -> c == '{').count() != tokenCount) {
            LOG.warn("Ignoring an invalid span name template: event_type={} template={}", eventType, template);
            return null;
        }
        return parts.toString();
    }

    private static void appendLiteral(StringBuilder parts, String literal) {
        if (!literal.isEmpty()) {
            appendPart(parts, "'%s'".formatted(quoted(literal)));
        }
    }

    private static void appendPart(StringBuilder parts, String part) {
        if (!parts.isEmpty()) {
            parts.append(" || ");
        }
        parts.append(part);
    }

    /** SQL string-literal escaping; the one escape SQL has. */
    private static String quoted(String value) {
        return value.replace("'", "''");
    }
}
