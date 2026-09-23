/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import cafe.jeffrey.profile.heapdump.view.HeapView;

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
