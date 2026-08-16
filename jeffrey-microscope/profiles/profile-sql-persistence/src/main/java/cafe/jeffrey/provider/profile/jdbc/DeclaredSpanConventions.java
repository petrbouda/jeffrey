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
 * The span conventions a recording declares for itself — {@code @SpanName} templates and
 * {@code @SpanOutcome} field-and-semantics pairs, carried in the recording's metadata and stored by
 * the parser in {@code event_types.extras} — rendered into the same CASE-arm SQL shape as the
 * built-ins in {@link SpanConventions}.
 * <p>
 * This is what makes the conventions generic: an event type Jeffrey has never seen annotates
 * itself, and the derivation names and judges it with no change here — the same way span discovery
 * finds it structurally by its {@code spanId} field. The built-ins remain only for Jeffrey's own
 * types on recordings that predate the annotations, and a declared convention outranks them.
 *
 * <h2>Trust boundary</h2>
 * Everything read here comes out of an arbitrary recording and ends up inside a SQL statement, so
 * nothing is interpolated unvalidated: a template token or an outcome field must match
 * {@code [A-Za-z0-9_]+}, literal text and event-type names have their quotes doubled, and an
 * unknown semantics is skipped. A declaration that fails any of this is logged and dropped — the
 * event type falls back to what it recorded — and {@code derive()} never fails on it.
 */
final class DeclaredSpanConventions {

    private static final Logger LOG = LoggerFactory.getLogger(DeclaredSpanConventions.class);

    /** The SQL rendered when nothing is declared, so the projections need no special case. */
    static final String NONE = "NULL";

    /** A template names fields as {@code {token}}; anything between tokens is literal text. */
    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    /** The only thing a token or an outcome field may be: a plain JSON key, never SQL. */
    private static final Pattern SAFE_FIELD = Pattern.compile("[A-Za-z0-9_]+");

    //language=SQL
    private static final String DECLARED_CONVENTIONS = """
            SELECT
                name                                    AS event_type,
                json_extract_string(extras, '$.%s')     AS name_template,
                json_extract_string(extras, '$.%s')     AS outcome_from,
                json_extract_string(extras, '$.%s')     AS outcome_semantics
            FROM event_types
            WHERE json_extract_string(extras, '$.%s') IS NOT NULL
               OR json_extract_string(extras, '$.%s') IS NOT NULL
            """.formatted(
            SpanConventionKeys.EXTRAS_SPAN_NAME,
            SpanConventionKeys.EXTRAS_OUTCOME_FROM,
            SpanConventionKeys.EXTRAS_OUTCOME_SEMANTICS,
            SpanConventionKeys.EXTRAS_SPAN_NAME,
            SpanConventionKeys.EXTRAS_OUTCOME_FROM);

    /** One event type's declaration, as read from the profile — not yet validated. */
    private record Declaration(String eventType, String nameTemplate, String outcomeFrom, String outcomeSemantics) {
    }

    /** The rendered CASE expressions, {@link #NONE} where the profile declares nothing usable. */
    record Projections(String nameCase, String statusCase) {

        static final Projections EMPTY = new Projections(NONE, NONE);
    }

    private DeclaredSpanConventions() {
    }

    /**
     * Reads every declaration in the profile and renders the two CASE expressions the derivation
     * folds in ahead of the built-in conventions.
     */
    static Projections load(DatabaseClient databaseClient) {
        List<Declaration> declarations = databaseClient.query(
                StatementLabel.DERIVE_TRACE_SPANS,
                DECLARED_CONVENTIONS,
                new MapSqlParameterSource(),
                (rs, _) -> new Declaration(
                        rs.getString("event_type"),
                        rs.getString("name_template"),
                        rs.getString("outcome_from"),
                        rs.getString("outcome_semantics")));

        if (declarations.isEmpty()) {
            return Projections.EMPTY;
        }
        return new Projections(nameCase(declarations), statusCase(declarations));
    }

    private static String nameCase(List<Declaration> declarations) {
        StringBuilder arms = new StringBuilder();
        for (Declaration declaration : declarations) {
            String concatenation = renderTemplate(declaration);
            if (concatenation != null) {
                appendArm(arms, declaration.eventType(), concatenation);
            }
        }
        return caseOf(arms);
    }

    private static String statusCase(List<Declaration> declarations) {
        StringBuilder arms = new StringBuilder();
        for (Declaration declaration : declarations) {
            String outcome = renderOutcome(declaration);
            if (outcome != null) {
                appendArm(arms, declaration.eventType(), outcome);
            }
        }
        return caseOf(arms);
    }

    /**
     * The template as a SQL concatenation: tokens become JSON reads of the event's own fields,
     * literal runs become quoted literals. {@code ||} propagates NULL, so an event missing a
     * templated field derives a NULL name and falls through — the same behaviour the built-in
     * conventions have for an exchange without a URI.
     */
    private static String renderTemplate(Declaration declaration) {
        String template = declaration.nameTemplate();
        if (template == null) {
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
            LOG.warn("Ignoring an invalid span name template: event_type={} template={}",
                    declaration.eventType(), template);
            return null;
        }
        return parts.toString();
    }

    /** The declared outcome as the shared semantics arm, or null for a semantics unknown here. */
    private static String renderOutcome(Declaration declaration) {
        String from = declaration.outcomeFrom();
        String semantics = declaration.outcomeSemantics();
        if (from == null || semantics == null) {
            return null;
        }
        if (!SAFE_FIELD.matcher(from).matches()) {
            LOG.warn("Ignoring an invalid span outcome field: event_type={} field={}",
                    declaration.eventType(), from);
            return null;
        }

        String arm = SpanConventions.outcomeArm(semantics, "json_extract_string(fields, '$.%s')".formatted(from));
        if (arm == null) {
            // A semantics minted after this version of Jeffrey: skipped, never failed on.
            LOG.warn("Ignoring an unknown span outcome semantics: event_type={} semantics={}",
                    declaration.eventType(), semantics);
        }
        return arm;
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

    private static void appendArm(StringBuilder arms, String eventType, String expression) {
        arms.append("WHEN event_type = '%s' THEN %s\n".formatted(quoted(eventType), expression));
    }

    private static String caseOf(StringBuilder arms) {
        if (arms.isEmpty()) {
            return NONE;
        }
        return "CASE %s END".formatted(arms);
    }

    /** SQL string-literal escaping; the one escape SQL has. */
    private static String quoted(String value) {
        return value.replace("'", "''");
    }
}
