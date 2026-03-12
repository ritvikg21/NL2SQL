package com.example.nlsql.service;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.deparser.ExpressionDeParser;
import net.sf.jsqlparser.util.deparser.SelectDeParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * SqlSanitizerService (no BooleanValue — compatible with all JSqlParser versions)
 * - Replaces literal values with '?' placeholders
 * - Builds parameter list in order of appearance
 * - Supports: numbers, strings, dates, timestamps, IN(), null
 * - Safe for SELECT queries
 */
@Service
public class SqlSanitizerService {

    private static final Logger log = LoggerFactory.getLogger(SqlSanitizerService.class);

    public static class Sanitized {
        public final String sql;
        public final List<Object> params;

        public Sanitized(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }

    public static class SqlSanitizationException extends Exception {
        public SqlSanitizationException(String message, Throwable cause) {
            super(message, cause);
        }

        public SqlSanitizationException(String message) {
            super(message);
        }
    }

    public Sanitized sanitizeSelect(String sql) throws SqlSanitizationException {
        try {
            var stmt = CCJSqlParserUtil.parse(sql);
            if (!(stmt instanceof Select))
                throw new IllegalArgumentException("Only SELECT supported");

            List<Object> params = new ArrayList<>();
            StringBuilder buffer = new StringBuilder();

            ExpressionDeParser exprDeparser = new ExpressionDeParser() {

                @Override
                public void visit(LongValue longValue) {
                    params.add(longValue.getValue());
                    this.getBuffer().append("?");
                }

                @Override
                public void visit(DoubleValue doubleValue) {
                    params.add(doubleValue.getValue());
                    this.getBuffer().append("?");
                }

                @Override
                public void visit(StringValue stringValue) {
                    params.add(stringValue.getValue());
                    this.getBuffer().append("?");
                }

                @Override
                public void visit(DateValue dateValue) {
                    params.add(new Date(dateValue.getValue().getTime()));
                    this.getBuffer().append("?");
                }

                @Override
                public void visit(TimeValue timeValue) {
                    params.add(new Time(timeValue.getValue().getTime()));
                    this.getBuffer().append("?");
                }

                @Override
                public void visit(TimestampValue timestampValue) {
                    params.add(new Timestamp(timestampValue.getValue().getTime()));
                    this.getBuffer().append("?");
                }

                @Override
                public void visit(NullValue nullValue) {
                    params.add(null);
                    this.getBuffer().append("?");
                }

                @Override
                public void visit(ExpressionList expressionList) {
                    List<Expression> exprs = expressionList.getExpressions();
                    if (exprs == null || exprs.isEmpty()) {
                        this.getBuffer().append("()");
                        return;
                    }

                    this.getBuffer().append("(");
                    for (int i = 0; i < exprs.size(); i++) {
                        Expression e = exprs.get(i);

                        if (e instanceof StringValue) {
                            params.add(((StringValue) e).getValue());
                        } else if (e instanceof LongValue) {
                            params.add(((LongValue) e).getValue());
                        } else if (e instanceof DoubleValue) {
                            params.add(((DoubleValue) e).getValue());
                        } else if (e instanceof DateValue) {
                            params.add(new Date(((DateValue) e).getValue().getTime()));
                        } else if (e instanceof TimeValue) {
                            params.add(new Time(((TimeValue) e).getValue().getTime()));
                        } else if (e instanceof TimestampValue) {
                            params.add(new Timestamp(((TimestampValue) e).getValue().getTime()));
                        } else if (e instanceof NullValue) {
                            params.add(null);
                        } else {
                            throw new RuntimeException("Unsupported IN-list element: " + e.getClass().getSimpleName());
                        }

                        this.getBuffer().append("?");
                        if (i < exprs.size() - 1)
                            this.getBuffer().append(", ");
                    }
                    this.getBuffer().append(")");
                }

                @Override
                public void visit(Parenthesis parenthesis) {
                    this.getBuffer().append("(");
                    parenthesis.getExpression().accept(this);
                    this.getBuffer().append(")");
                }
            };

            SelectDeParser selectDeParser = new SelectDeParser(exprDeparser, buffer);
            exprDeparser.setSelectVisitor(selectDeParser);
            exprDeparser.setBuffer(buffer);

            Select select = (Select) stmt;
            select.getSelectBody().accept(selectDeParser);

            return new Sanitized(buffer.toString(), params);

        } catch (JSQLParserException | IllegalArgumentException ex) {
            log.warn("Failed to sanitize SQL: {}", ex.getMessage());
            throw new SqlSanitizationException("Failed to sanitize SQL", ex);
        } catch (RuntimeException ex) {
            log.warn("Unsupported SQL construct: {}", ex.getMessage());
            throw new SqlSanitizationException("Unsupported SQL construct: " + ex.getMessage(), ex);
        }
    }
}
