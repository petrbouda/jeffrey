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
