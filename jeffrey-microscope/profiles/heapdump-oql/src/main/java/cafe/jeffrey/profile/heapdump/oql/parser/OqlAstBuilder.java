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

import cafe.jeffrey.profile.heapdump.oql.ast.BinaryOperator;
import cafe.jeffrey.profile.heapdump.oql.ast.FromClause;
import cafe.jeffrey.profile.heapdump.oql.ast.LimitClause;
import cafe.jeffrey.profile.heapdump.oql.ast.ObjectSource;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlExpr;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlStatement.OqlQuery;
import cafe.jeffrey.profile.heapdump.oql.ast.OqlType;
import cafe.jeffrey.profile.heapdump.oql.ast.OrderItem;
import cafe.jeffrey.profile.heapdump.oql.ast.PathSegment;
import cafe.jeffrey.profile.heapdump.oql.ast.SelectClause;
import cafe.jeffrey.profile.heapdump.oql.ast.SelectClause.Projection;
import cafe.jeffrey.profile.heapdump.oql.ast.SelectClause.SelectModifier;
import cafe.jeffrey.profile.heapdump.oql.ast.UnaryOperator;
import cafe.jeffrey.profile.heapdump.oql.grammar.OqlBaseVisitor;
import cafe.jeffrey.profile.heapdump.oql.grammar.OqlParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks the ANTLR parse tree and produces a typed AST rooted at
 * {@link OqlStatement}. Sugar forms (map/filter/sort/top/unique) are emitted
 * unchanged here and rewritten by {@link SugarDesugarer} as a second pass.
 *
 * <p>Type tags on expression nodes are left as {@link OqlType#UNKNOWN}; they
 * get filled in by {@code OqlTypeResolver} after desugaring.
 */
final class OqlAstBuilder extends OqlBaseVisitor<Object> {

    @Override
    public OqlStatement visitStatement(OqlParser.StatementContext ctx) {
        List<OqlParser.QueryAtomContext> branches = ctx.queryAtom();
        if (branches.size() == 1) {
            return (OqlQuery) visit(branches.get(0).query());
        }
        List<OqlQuery> queries = new ArrayList<>(branches.size());
        for (OqlParser.QueryAtomContext atom : branches) {
            queries.add((OqlQuery) visit(atom.query()));
        }
        return new OqlStatement.UnionQuery(queries);
    }

    @Override
    public OqlQuery visitQuery(OqlParser.QueryContext ctx) {
        SelectClause select = (SelectClause) visit(ctx.selectClause());
        FromClause from = (FromClause) visit(ctx.fromClause());

        OqlExpr where = ctx.whereClause() != null
                ? (OqlExpr) visit(ctx.whereClause().expression())
                : null;

        List<OqlExpr> groupBy = ctx.groupByClause() != null
                ? buildExpressionList(ctx.groupByClause().expression())
                : List.of();

        OqlExpr having = ctx.havingClause() != null
                ? (OqlExpr) visit(ctx.havingClause().expression())
                : null;

        List<OrderItem> orderBy = ctx.orderByClause() != null
                ? buildOrderItems(ctx.orderByClause().orderItem())
                : List.of();

        LimitClause limit = ctx.limitClause() != null
                ? buildLimit(ctx.limitClause())
                : null;

        return new OqlQuery(select, from, where, groupBy, having, orderBy, limit);
    }

    private List<OqlExpr> buildExpressionList(List<OqlParser.ExpressionContext> ctxs) {
        List<OqlExpr> out = new ArrayList<>(ctxs.size());
        for (OqlParser.ExpressionContext c : ctxs) {
            out.add((OqlExpr) visit(c));
        }
        return out;
    }

    private List<OrderItem> buildOrderItems(List<OqlParser.OrderItemContext> ctxs) {
        List<OrderItem> out = new ArrayList<>(ctxs.size());
        for (OqlParser.OrderItemContext c : ctxs) {
            OqlExpr expr = (OqlExpr) visit(c.expression());
            boolean descending = c.DESC() != null;
            out.add(new OrderItem(expr, descending));
        }
        return out;
    }

    private LimitClause buildLimit(OqlParser.LimitClauseContext ctx) {
        long limit = Long.parseLong(ctx.n.getText());
        long offset = ctx.m != null ? Long.parseLong(ctx.m.getText()) : 0L;
        return new LimitClause(limit, offset);
    }

    @Override
    public SelectClause visitSelectClause(OqlParser.SelectClauseContext ctx) {
        SelectModifier modifier = SelectModifier.NONE;
        OqlParser.SelectModifierContext mod = ctx.selectModifier();
        if (mod != null) {
            modifier = mod.DISTINCT() != null
                    ? SelectModifier.DISTINCT
                    : SelectModifier.AS_RETAINED_SET;
        }
        OqlParser.SelectListContext list = ctx.selectList();
        if (list.STAR() != null) {
            return new SelectClause(modifier, SelectClause.STAR_PROJECTION);
        }
        List<Projection> projections = new ArrayList<>(list.selectItem().size());
        for (OqlParser.SelectItemContext item : list.selectItem()) {
            OqlExpr expr = (OqlExpr) visit(item.expression());
            String alias = item.alias != null ? item.alias.getText() : null;
            boolean objects = item.OBJECTS() != null;
            projections.add(new Projection(expr, alias, false, objects));
        }
        return new SelectClause(modifier, projections);
    }

    @Override
    public FromClause visitFromClause(OqlParser.FromClauseContext ctx) {
        FromClause.FromKind kind = FromClause.FromKind.NONE;
        if (ctx.fromKind() != null) {
            kind = ctx.fromKind().INSTANCEOF() != null
                    ? FromClause.FromKind.INSTANCEOF
                    : FromClause.FromKind.IMPLEMENTS;
        }
        ObjectSource source = (ObjectSource) visit(ctx.fromSource());
        String alias = ctx.alias != null ? ctx.alias.getText() : null;
        return new FromClause(kind, source, alias);
    }

    @Override
    public ObjectSource visitFromClassName(OqlParser.FromClassNameContext ctx) {
        return new ObjectSource.ClassSource(ctx.className().getText());
    }

    @Override
    public ObjectSource visitFromRegex(OqlParser.FromRegexContext ctx) {
        return new ObjectSource.RegexSource(unquote(ctx.STRING_LITERAL().getText()));
    }

    @Override
    public ObjectSource visitFromSubquery(OqlParser.FromSubqueryContext ctx) {
        OqlQuery q = (OqlQuery) visit(ctx.query());
        return new ObjectSource.SubquerySource(q);
    }

    @Override
    public ObjectSource visitFromHeapHelper(OqlParser.FromHeapHelperContext ctx) {
        OqlParser.QualifiedHeapHelperContext q = ctx.qualifiedHeapHelper();
        String name = "heap." + q.identifier().getText();
        List<OqlExpr> args = q.argList() != null
                ? buildExpressionList(q.argList().expression())
                : List.of();
        return new ObjectSource.FunctionSource(name, args);
    }

    @Override
    public ObjectSource visitFromFunctionCall(OqlParser.FromFunctionCallContext ctx) {
        OqlParser.FunctionCallContext fc = ctx.functionCall();
        String name = fc.identifier().getText();
        List<OqlExpr> args = fc.argList() != null
                ? buildExpressionList(fc.argList().expression())
                : List.of();
        return new ObjectSource.FunctionSource(name, args);
    }

    // ---- Expressions --------------------------------------------------

    @Override
    public OqlExpr visitOrExpr(OqlParser.OrExprContext ctx) {
        List<OqlParser.AndExprContext> parts = ctx.andExpr();
        OqlExpr left = (OqlExpr) visit(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            OqlExpr right = (OqlExpr) visit(parts.get(i));
            left = new OqlExpr.BinaryOp(BinaryOperator.OR, left, right, OqlType.BOOLEAN);
        }
        return left;
    }

    @Override
    public OqlExpr visitAndExpr(OqlParser.AndExprContext ctx) {
        List<OqlParser.NotExprContext> parts = ctx.notExpr();
        OqlExpr left = (OqlExpr) visit(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            OqlExpr right = (OqlExpr) visit(parts.get(i));
            left = new OqlExpr.BinaryOp(BinaryOperator.AND, left, right, OqlType.BOOLEAN);
        }
        return left;
    }

    @Override
    public OqlExpr visitNotExpression(OqlParser.NotExpressionContext ctx) {
        OqlExpr operand = (OqlExpr) visit(ctx.notExpr());
        return new OqlExpr.UnaryOp(UnaryOperator.NOT, operand, OqlType.BOOLEAN);
    }

    @Override
    public OqlExpr visitComparisonPassthrough(OqlParser.ComparisonPassthroughContext ctx) {
        return (OqlExpr) visit(ctx.comparisonExpr());
    }

    @Override
    public OqlExpr visitBinaryCompare(OqlParser.BinaryCompareContext ctx) {
        OqlExpr left = (OqlExpr) visit(ctx.additiveExpr(0));
        if (ctx.additiveExpr().size() == 1) {
            // No operator: bubble up the inner expression unchanged.
            return left;
        }
        OqlExpr right = (OqlExpr) visit(ctx.additiveExpr(1));
        String opText = ctx.getChild(1).getText();
        BinaryOperator op = switch (opText) {
            case "=" -> BinaryOperator.EQ;
            case "!=", "<>" -> BinaryOperator.NEQ;
            case "<" -> BinaryOperator.LT;
            case "<=" -> BinaryOperator.LTE;
            case ">" -> BinaryOperator.GT;
            case ">=" -> BinaryOperator.GTE;
            default -> throw new OqlParseException(
                    "Unknown comparison operator: " + opText,
                    ctx.getStart().getLine(), ctx.getStart().getCharPositionInLine());
        };
        return new OqlExpr.BinaryOp(op, left, right, OqlType.BOOLEAN);
    }

    @Override
    public OqlExpr visitLikeCompare(OqlParser.LikeCompareContext ctx) {
        OqlExpr left = (OqlExpr) visit(ctx.additiveExpr(0));
        OqlExpr right = (OqlExpr) visit(ctx.additiveExpr(1));
        return new OqlExpr.BinaryOp(BinaryOperator.LIKE, left, right, OqlType.BOOLEAN);
    }

    @Override
    public OqlExpr visitInCompare(OqlParser.InCompareContext ctx) {
        OqlExpr left = (OqlExpr) visit(ctx.additiveExpr());
        List<OqlExpr> values = buildExpressionList(ctx.expression());
        boolean negate = ctx.NOT() != null;
        return new OqlExpr.InOp(left, values, negate, OqlType.BOOLEAN);
    }

    @Override
    public OqlExpr visitIsNullCompare(OqlParser.IsNullCompareContext ctx) {
        OqlExpr operand = (OqlExpr) visit(ctx.additiveExpr());
        return new OqlExpr.NullCheck(operand, false, OqlType.BOOLEAN);
    }

    @Override
    public OqlExpr visitIsNotNullCompare(OqlParser.IsNotNullCompareContext ctx) {
        OqlExpr operand = (OqlExpr) visit(ctx.additiveExpr());
        return new OqlExpr.NullCheck(operand, true, OqlType.BOOLEAN);
    }

    @Override
    public OqlExpr visitAdditiveExpr(OqlParser.AdditiveExprContext ctx) {
        List<OqlParser.MultiplicativeExprContext> parts = ctx.multiplicativeExpr();
        OqlExpr left = (OqlExpr) visit(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            String opText = ctx.getChild(2 * i - 1).getText();
            BinaryOperator op = "+".equals(opText) ? BinaryOperator.ADD : BinaryOperator.SUB;
            OqlExpr right = (OqlExpr) visit(parts.get(i));
            left = new OqlExpr.BinaryOp(op, left, right, OqlType.NUMBER);
        }
        return left;
    }

    @Override
    public OqlExpr visitMultiplicativeExpr(OqlParser.MultiplicativeExprContext ctx) {
        List<OqlParser.UnaryExprContext> parts = ctx.unaryExpr();
        OqlExpr left = (OqlExpr) visit(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            String opText = ctx.getChild(2 * i - 1).getText();
            BinaryOperator op = "*".equals(opText) ? BinaryOperator.MUL : BinaryOperator.DIV;
            OqlExpr right = (OqlExpr) visit(parts.get(i));
            left = new OqlExpr.BinaryOp(op, left, right, OqlType.NUMBER);
        }
        return left;
    }

    @Override
    public OqlExpr visitNegation(OqlParser.NegationContext ctx) {
        OqlExpr operand = (OqlExpr) visit(ctx.unaryExpr());
        return new OqlExpr.UnaryOp(UnaryOperator.NEG, operand, OqlType.NUMBER);
    }

    @Override
    public OqlExpr visitPostfixPassthrough(OqlParser.PostfixPassthroughContext ctx) {
        return (OqlExpr) visit(ctx.postfixExpr());
    }

    @Override
    public OqlExpr visitPostfixExpr(OqlParser.PostfixExprContext ctx) {
        OqlExpr root = (OqlExpr) visit(ctx.primaryExpr());
        if (ctx.pathSegment().isEmpty() && ctx.indexSegment().isEmpty()) {
            return root;
        }
        List<PathSegment> segments = new ArrayList<>();
        // Walk the child stream so we preserve the original interleaved order
        // of dot- and index-segments.
        for (int i = 1; i < ctx.getChildCount(); i++) {
            var child = ctx.getChild(i);
            if (child instanceof OqlParser.FieldPathSegmentContext fp) {
                segments.add(new PathSegment.Field(fp.identifier().getText()));
            } else if (child instanceof OqlParser.AttrPathSegmentContext ap) {
                segments.add(new PathSegment.AttrField(ap.identifier().getText()));
            } else if (child instanceof OqlParser.IndexSegmentContext idx) {
                OqlExpr indexExpr = (OqlExpr) visit(idx.expression());
                segments.add(new PathSegment.Index(indexExpr));
            }
        }
        return new OqlExpr.PathExpr(root, segments, OqlType.UNKNOWN);
    }

    @Override
    public OqlExpr visitLiteralPrimary(OqlParser.LiteralPrimaryContext ctx) {
        return (OqlExpr) visit(ctx.literal());
    }

    @Override
    public OqlExpr visitCasePrimary(OqlParser.CasePrimaryContext ctx) {
        return (OqlExpr) visit(ctx.caseExpression());
    }

    @Override
    public OqlExpr visitFunctionCallPrimary(OqlParser.FunctionCallPrimaryContext ctx) {
        return (OqlExpr) visit(ctx.functionCall());
    }

    @Override
    public OqlExpr visitAttrRefPrimary(OqlParser.AttrRefPrimaryContext ctx) {
        String name = ctx.attrRef().identifier().getText();
        return new OqlExpr.AttrRef(name, OqlType.UNKNOWN);
    }

    @Override
    public OqlExpr visitHeapHelperPrimary(OqlParser.HeapHelperPrimaryContext ctx) {
        OqlParser.QualifiedHeapHelperContext q = ctx.qualifiedHeapHelper();
        String funcName = "heap." + q.identifier().getText();
        List<OqlExpr> args = q.argList() != null ? buildExpressionList(q.argList().expression()) : List.of();
        return new OqlExpr.FunctionCall(funcName, args, false, OqlType.UNKNOWN);
    }

    @Override
    public OqlExpr visitBindingRefPrimary(OqlParser.BindingRefPrimaryContext ctx) {
        return new OqlExpr.BindingRef(ctx.identifier().getText(), OqlType.UNKNOWN);
    }

    @Override
    public OqlExpr visitParenExpression(OqlParser.ParenExpressionContext ctx) {
        return (OqlExpr) visit(ctx.expression());
    }

    @Override
    public OqlExpr visitSubqueryPrimary(OqlParser.SubqueryPrimaryContext ctx) {
        OqlQuery q = (OqlQuery) visit(ctx.query());
        return new OqlExpr.SubqueryExpr(q, OqlType.SET_OF_INSTANCES);
    }

    @Override
    public OqlExpr visitFunctionCall(OqlParser.FunctionCallContext ctx) {
        String name = ctx.identifier().getText();
        if (ctx.STAR() != null) {
            return new OqlExpr.FunctionCall(name, List.of(), true, OqlType.UNKNOWN);
        }
        List<OqlExpr> args = ctx.argList() != null
                ? buildExpressionList(ctx.argList().expression())
                : List.of();
        return new OqlExpr.FunctionCall(name, args, false, OqlType.UNKNOWN);
    }

    @Override
    public OqlExpr visitCaseExpression(OqlParser.CaseExpressionContext ctx) {
        List<OqlExpr.CaseExpr.WhenClause> whens = new ArrayList<>(ctx.whenClause().size());
        for (OqlParser.WhenClauseContext w : ctx.whenClause()) {
            OqlExpr cond = (OqlExpr) visit(w.expression(0));
            OqlExpr result = (OqlExpr) visit(w.expression(1));
            whens.add(new OqlExpr.CaseExpr.WhenClause(cond, result));
        }
        OqlExpr elseExpr = ctx.expression() != null ? (OqlExpr) visit(ctx.expression()) : null;
        return new OqlExpr.CaseExpr(whens, elseExpr, OqlType.UNKNOWN);
    }

    @Override
    public OqlExpr visitIntLiteral(OqlParser.IntLiteralContext ctx) {
        return new OqlExpr.Literal(Long.parseLong(ctx.INTEGER_LITERAL().getText()), OqlType.NUMBER);
    }

    @Override
    public OqlExpr visitHexLiteral(OqlParser.HexLiteralContext ctx) {
        String text = ctx.HEX_LITERAL().getText();
        return new OqlExpr.Literal(Long.parseUnsignedLong(text.substring(2), 16), OqlType.NUMBER);
    }

    @Override
    public OqlExpr visitDecimalLiteral(OqlParser.DecimalLiteralContext ctx) {
        return new OqlExpr.Literal(Double.parseDouble(ctx.DECIMAL_LITERAL().getText()), OqlType.NUMBER);
    }

    @Override
    public OqlExpr visitStringLiteral(OqlParser.StringLiteralContext ctx) {
        return new OqlExpr.Literal(unquote(ctx.STRING_LITERAL().getText()), OqlType.STRING);
    }

    @Override
    public OqlExpr visitTrueLiteral(OqlParser.TrueLiteralContext ctx) {
        return new OqlExpr.Literal(Boolean.TRUE, OqlType.BOOLEAN);
    }

    @Override
    public OqlExpr visitFalseLiteral(OqlParser.FalseLiteralContext ctx) {
        return new OqlExpr.Literal(Boolean.FALSE, OqlType.BOOLEAN);
    }

    @Override
    public OqlExpr visitNullLiteral(OqlParser.NullLiteralContext ctx) {
        return new OqlExpr.Literal(null, OqlType.UNKNOWN);
    }

    // ---- Helpers ------------------------------------------------------

    private static String unquote(String quoted) {
        if (quoted == null || quoted.length() < 2) {
            return quoted;
        }
        // Strip surrounding quote chars and process \X escapes.
        StringBuilder out = new StringBuilder(quoted.length() - 2);
        for (int i = 1; i < quoted.length() - 1; i++) {
            char ch = quoted.charAt(i);
            if (ch == '\\' && i + 1 < quoted.length() - 1) {
                char next = quoted.charAt(++i);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case '\\' -> out.append('\\');
                    case '\'' -> out.append('\'');
                    case '"' -> out.append('"');
                    default -> out.append(next);
                }
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static int line(ParserRuleContext ctx) {
        return ctx.getStart().getLine();
    }

    private static int col(ParserRuleContext ctx) {
        return ctx.getStart().getCharPositionInLine();
    }

    @SuppressWarnings("unused")
    private static String text(TerminalNode node) {
        return node == null ? null : node.getText();
    }
}
