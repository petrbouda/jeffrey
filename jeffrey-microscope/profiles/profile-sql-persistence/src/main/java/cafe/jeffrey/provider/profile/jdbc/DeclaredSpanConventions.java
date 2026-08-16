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

import cafe.jeffrey.shared.common.model.SpanConventionKeys;
import cafe.jeffrey.shared.persistence.StatementLabel;
import cafe.jeffrey.shared.persistence.client.DatabaseClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The naming conventions a recording declares for itself — {@code @SpanName} templates, carried in
 * the recording's metadata and stored by the parser in {@code event_types.extras} — rendered into
 * the same CASE-arm SQL shape as the built-ins in {@link SpanConventions}.
 * <p>
 * This is what makes naming generic: an event type Jeffrey has never seen annotates itself, and
 * the derivation names it with no change here — the same way span discovery finds it structurally
 * by its {@code spanId} field. The built-ins remain only for Jeffrey's own types on recordings
 * that predate the annotation, and a declared template outranks them.
 * <p>
 * Only the name is declarable. A span's verdict is the writer's statement — recorded through
 * {@code commitSpan()}/{@code failed()} — so there is deliberately nothing here that judges an
 * outcome; an event type outside the built-ins is judged solely by the status it recorded.
 *
 * <h2>Trust boundary</h2>
 * Everything read here comes out of an arbitrary recording and ends up inside a SQL statement, so
 * nothing is interpolated unvalidated: a template token must match {@code [A-Za-z0-9_]+}, literal
 * text and event-type names have their quotes doubled. A template that fails any of this is logged
 * and dropped — the event type falls back to what it recorded — and {@code derive()} never fails
 * on it.
 */
final class DeclaredSpanConventions {

    private static final Logger LOG = LoggerFactory.getLogger(DeclaredSpanConventions.class);

    /** The SQL rendered when nothing is declared, so the projections need no special case. */
    static final String NONE = "NULL";

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

    private DeclaredSpanConventions() {
    }

    /**
     * Reads every declared template in the profile and renders the CASE expression the name
     * projection folds in ahead of the built-in conventions — {@link #NONE} when the profile
     * declares nothing usable.
     */
    static String nameCase(DatabaseClient databaseClient) {
        List<Declaration> declarations = databaseClient.query(
                StatementLabel.DERIVE_TRACE_SPANS,
                DECLARED_TEMPLATES,
                new MapSqlParameterSource(),
                (rs, _) -> new Declaration(rs.getString("event_type"), rs.getString("name_template")));

        StringBuilder arms = new StringBuilder();
        for (Declaration declaration : declarations) {
            String concatenation = renderTemplate(declaration);
            if (concatenation != null) {
                arms.append("WHEN event_type = '%s' THEN %s\n"
                        .formatted(quoted(declaration.eventType()), concatenation));
            }
        }
        if (arms.isEmpty()) {
            return NONE;
        }
        return "CASE %s END".formatted(arms);
    }

    /**
     * The template as a SQL concatenation: tokens become JSON reads of the event's own fields,
     * literal runs become quoted literals. {@code ||} propagates NULL, so an event missing a
     * templated field derives a NULL name and falls through — the same behaviour the built-in
     * conventions have for an exchange without a URI.
     */
    private static String renderTemplate(Declaration declaration) {
        String template = declaration.nameTemplate();

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
            LOG.warn("Ignoring an invalid span name template: event_type={} template={}",
                    declaration.eventType(), template);
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
