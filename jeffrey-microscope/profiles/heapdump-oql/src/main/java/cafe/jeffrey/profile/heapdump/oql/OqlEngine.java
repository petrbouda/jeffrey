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
package cafe.jeffrey.profile.heapdump.oql;

import cafe.jeffrey.profile.heapdump.model.OQLQueryResult;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement;
import cafe.jeffrey.profile.heapdump.oql.compiler.ExecutionPlan;
import cafe.jeffrey.profile.heapdump.oql.compiler.OqlCompileOptions;
import cafe.jeffrey.profile.heapdump.oql.compiler.OqlCompiler;
import cafe.jeffrey.profile.heapdump.oql.executor.OqlExecutor;
import cafe.jeffrey.profile.heapdump.oql.parser.OqlParseException;
import cafe.jeffrey.profile.heapdump.oql.parser.OqlParserFactory;
import cafe.jeffrey.profile.heapdump.parser.HeapView;

import java.sql.SQLException;

/**
 * Public façade for the OQL engine. Bundles parse, compile, and execute.
 *
 * <p>Thread-safe — instances may be shared across requests. ANTLR's parser
 * itself is not thread-safe, so the engine creates a fresh parser per call.
 */
public final class OqlEngine {

    private final OqlCompiler compiler = new OqlCompiler();

    /**
     * Parses an OQL query string into a typed AST. Throws
     * {@link OqlParseException} on syntax or type errors.
     */
    public OqlStatement parse(String oql) {
        if (oql == null || oql.isBlank()) {
            throw new OqlParseException("Query is empty");
        }
        return OqlParserFactory.parse(oql);
    }

    /**
     * Compiles a parsed statement into a concrete execution plan ({@link ExecutionPlan.SqlPlan},
     * {@link ExecutionPlan.HybridPlan}, or {@link ExecutionPlan.JavaPlan}).
     */
    public ExecutionPlan compile(OqlStatement stmt) {
        return compiler.compile(stmt);
    }

    /**
     * Compile overload accepting per-query knobs such as
     * {@code scanLargeStrings} (enables the {@link ExecutionPlan.StringFallbackPlan}
     * Plan-C tail scan over Strings that exceeded the indexer's content cap).
     */
    public ExecutionPlan compile(OqlStatement stmt, OqlCompileOptions options) {
        return compiler.compile(stmt, options);
    }

    /** Executes a previously compiled plan against the given view. */
    public OQLQueryResult execute(ExecutionPlan plan, HeapView view, int limit) throws SQLException {
        return OqlExecutor.execute(plan, view, limit);
    }
}
