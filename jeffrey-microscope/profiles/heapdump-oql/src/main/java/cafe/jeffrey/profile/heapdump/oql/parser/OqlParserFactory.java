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
package cafe.jeffrey.profile.heapdump.oql.parser;

import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement;
import cafe.jeffrey.profile.heapdump.oql.grammar.OqlLexer;
import cafe.jeffrey.profile.heapdump.oql.grammar.OqlParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Internal entry that wires ANTLR lexer → ANTLR parser → AST builder → type
 * resolver. Public callers go through {@link cafe.jeffrey.profile.heapdump.oql.OqlEngine}.
 */
public final class OqlParserFactory {

    private OqlParserFactory() {
    }

    public static OqlStatement parse(String oql) {
        OqlLexer lexer = new OqlLexer(CharStreams.fromString(oql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(THROWING_LISTENER);

        OqlParser parser = new OqlParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(THROWING_LISTENER);

        OqlParser.StatementContext tree = parser.statement();
        OqlStatement ast = (OqlStatement) new OqlAstBuilder().visit(tree);
        new OqlTypeResolver().resolve(ast);
        return ast;
    }

    private static final BaseErrorListener THROWING_LISTENER = new BaseErrorListener() {
        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e) {
            throw new OqlParseException(msg, line, charPositionInLine, e);
        }
    };
}
