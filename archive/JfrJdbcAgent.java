/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Listener;
import net.bytebuddy.agent.builder.AgentBuilder.Listener.StreamWriting;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.*;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.support.SqlLobValue;
import pbouda.jeffrey.jfr.types.jdbc.*;
import pbouda.jeffrey.provider.api.streamer.model.*;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static net.bytebuddy.matcher.ElementMatchers.anyOf;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class JfrJdbcAgent {

    private static final String NAMED_PARAM_MAPPED_QUERY_SPEC_NAME =
            "org.springframework.jdbc.core.simple.DefaultJdbcClient$DefaultStatementSpec$NamedParamMappedQuerySpec";

    private static final String INDEXED_PARAM_MAPPED_QUERY_SPEC_NAME =
            "org.springframework.jdbc.core.simple.DefaultJdbcClient$DefaultStatementSpec$IndexedParamMappedQuerySpec";

    private static final String DEFAULT_STATEMENT_SPEC =
            "org.springframework.jdbc.core.simple.DefaultJdbcClient$DefaultStatementSpec";

    public static void premain(String agentArgs, Instrumentation inst) {
        startAgent(inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        startAgent(inst);
    }

    private static void startAgent(Instrumentation inst) {
        ElementMatcher<? super MethodDescription> queryMatcher = methodDesc ->
                methodDesc.isMethod() &&
                (methodDesc.getActualName().equals("list") || methodDesc.getActualName().equals("stream"));

        ElementMatcher<? super MethodDescription> updateMatcher = methodDesc ->
                methodDesc.isMethod() && methodDesc.getActualName().equals("update");

        ElementMatcher.Junction<MethodDescription> simpleUpdateMethod = named("update")
                .and(ElementMatchers.takesArguments(String.class));

        ElementMatcher.Junction<MethodDescription> simpleExecuteMethod = named("execute")
                .and(ElementMatchers.takesArguments(String.class));

        ElementMatcher.Junction<TypeDescription> classSelector =
                named(NAMED_PARAM_MAPPED_QUERY_SPEC_NAME)
                        .or(named(INDEXED_PARAM_MAPPED_QUERY_SPEC_NAME))
                        .or(named(DEFAULT_STATEMENT_SPEC));

        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(new Listener.WithErrorsOnly(StreamWriting.toSystemOut()))
                .with(new Listener.WithTransformationsOnly(StreamWriting.toSystemOut()))
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(classSelector)
                .transform((builder, _, _, _, _) ->
                        builder
                                .visit(Advice.to(JdbcClientQueryAdvice.class).on(queryMatcher))
                                .visit(Advice.to(JdbcClientUpdateAdvice.class).on(updateMatcher)))
                .installOn(inst);

        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(new Listener.WithErrorsOnly(StreamWriting.toSystemOut()))
                .with(new Listener.WithTransformationsOnly(StreamWriting.toSystemOut()))
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(anyOf(JdbcTemplate.class))
                .transform((builder, _, _, _, _) ->
                        builder.visit(Advice.to(JdbcTemplateUpdateAdvice.class)
                                        .on(simpleUpdateMethod))
                                .visit(Advice.to(JdbcTemplateUpdateAdvice.class)
                                        .on(simpleExecuteMethod)))
                .installOn(inst);
    }

    public static class JdbcTemplateUpdateAdvice {
        @OnMethodEnter
        static JdbcBaseEvent enter(
                @Origin("#m") String originMethod,
                @Argument(0) String sql) {

            List<String> lines = sql.lines().toList();
            if (lines.isEmpty()) {
                return null;
            }

            JdbcBaseEvent event;
            // Execution of the BEGIN TRANSACTION
            if (originMethod.startsWith("update")
                && lines.size() > 2 && lines.getFirst().stripLeading().startsWith("BEGIN TRANSACTION")
                && lines.get(1).stripLeading().startsWith("DELETE FROM")) {
                event = new JdbcDeleteEvent();
            } else {
                event = new JdbcGenericEvent();
            }

            event.sql = sql;
            return event;
        }

        @OnMethodExit
        static void exit(
                @Return(typing = Typing.DYNAMIC, readOnly = false) Object result,
                @Enter JdbcBaseEvent event) {

            if (event == null) {
                System.out.println("Event is `null` in JdbcTemplateUpdateAdvice");
                return;
            }

            event.rows = (Long) result;
            event.commit();
        }
    }

    /**
     * Advice for {@link #NAMED_PARAM_MAPPED_QUERY_SPEC_NAME} and {@link #INDEXED_PARAM_MAPPED_QUERY_SPEC_NAME}
     * that captures the SQL query and parameters from the outer object.
     */
    public static class JdbcClientQueryAdvice {
        @OnMethodEnter
        static JdbcQueryEvent enter(
                @Origin("#m") String originMethod,
                @This Object thisObject) {

            JdbcQueryEvent event = switch (originMethod) {
                case "list" -> new JdbcQueryEvent();
                case "stream" -> new JdbcStreamEvent();
                default -> {
                    System.out.println("Unknown method: " + originMethod);
                    yield new JdbcQueryEvent();
                }
            };

            try {
                Field nameField = thisObject.getClass().getDeclaredField("this$1");
                Object outerObject = nameField.get(thisObject);

                Class<?> outerClass = outerObject.getClass();
                Field sqlField = outerClass.getDeclaredField("sql");
                String sql = (String) sqlField.get(outerObject);

                Field paramsField = outerClass.getDeclaredField("namedParamSource");
                SqlParameterSource params = (SqlParameterSource) paramsField.get(outerObject);

                event.sql = sql;
                event.params = JfrJdbcAgent.paramSourceToString(params);
            } catch (Exception e) {
                System.out.println("Failed to get 'this$1' field representing outer object");
            }

            event.begin();
            return event;
        }

        @OnMethodExit
        static void exit(
                @Return(typing = Typing.DYNAMIC, readOnly = false) Object result,
                @Enter JdbcQueryEvent event) {

            if (result instanceof Stream<?> stream) {
                Counter counter = new Counter();
                result = stream
                        .peek(counter)
                        .onClose(new Closer(event, counter));
            } else {
                event.end();
                event.rows = (Long) switch (result) {
                    case List<?> list -> list.size();
                    case null -> (Long) null; // very likely RowCallbackHandler was used
                    default -> 1; // very likely ResultSetExtractor converting to single object was used
                };
                event.samples = event.rows;
                event.commit();
            }
        }
    }

    /**
     * Advice for {@link #DEFAULT_STATEMENT_SPEC} that captures the SQL query and parameters from THIS object.
     * It determines the type of operation (INSERT, UPDATE, DELETE) based on the SQL statement.
     */
    public static class JdbcClientUpdateAdvice {
        @OnMethodEnter
        static void enter(
                @This Object thisObject,
                @Local("event") JdbcBaseEvent event) {
            try {
                Field sqlField = thisObject.getClass().getDeclaredField("sql");
                String sql = (String) sqlField.get(thisObject);

                Field namedParamsField = thisObject.getClass().getDeclaredField("namedParamSource");
                SqlParameterSource namedParamSource = (SqlParameterSource) namedParamsField.get(thisObject);

                String params = JfrJdbcAgent.paramSourceToString(namedParamSource);
                if (params == null) {
                    Field indexedParamsField = thisObject.getClass().getDeclaredField("indexedParams");
                    @SuppressWarnings("unchecked")
                    List<Object> indexedParams = (List<Object>) indexedParamsField.get(thisObject);
                    params = JfrJdbcAgent.indexedParamsToString(indexedParams);
                }

                if (sql != null) {
                    if (sql.startsWith("INSERT ")) {
                        event = new JdbcInsertEvent();
                    } else if (sql.startsWith("UPDATE ")) {
                        event = new JdbcUpdateEvent();
                    } else if (sql.startsWith("DELETE ")) {
                        event = new JdbcDeleteEvent();
                    } else {
                        event = new JdbcGenericEvent();
                    }

                    event.params = params;
                    event.sql = sql;
                    event.begin();
                }
            } catch (Exception e) {
                System.out.println("Failed to get 'this$1' field representing outer object");
            }
        }

        @OnMethodExit
        static void exit(
                @Return(typing = Typing.DYNAMIC, readOnly = false) Integer result,
                @Local("event") JdbcBaseEvent event) {

            if (event == null) {
                return;
            }

            event.rows = Long.valueOf(result);
            event.commit();
        }
    }

    public static class Closer implements Runnable {

        private final JdbcQueryEvent event;
        private final Counter counter;

        public Closer(JdbcQueryEvent event, Counter counter) {
            this.event = event;
            this.counter = counter;
        }

        @Override
        public void run() {
            event.end();
            event.rows = counter.rows();
            event.samples = counter.samples();
            event.commit();
        }
    }

    public static class Counter implements Consumer<Object> {

        private final LongAdder rows = new LongAdder();
        private final LongAdder samples = new LongAdder();

        @Override
        public void accept(Object o) {
            long collectedSamples = switch (o) {
                case GenericRecord record -> record.samples();
                case FlamegraphRecord record -> record.samples();
                case TimeseriesRecord record -> {
                    long samples = 0;
                    for (SecondValue value : record.values()) {
                        samples += value.value();
                    }
                    yield samples;
                }
                case SubSecondRecord record -> record.value();
                default -> 1;
            };

            samples.add(collectedSamples);
            rows.increment();
        }

        public long rows() {
            return rows.longValue();
        }

        public long samples() {
            return samples.longValue();
        }
    }

    public static String indexedParamsToString(List<Object> params) {
        if (params != null && !params.isEmpty()) {
            StringJoiner joiner = new StringJoiner(", ", "{", "}");
            for (int i = 0; i < params.size(); i++) {
                addParam(joiner, String.valueOf(i), params.get(i));
            }
            return joiner.toString();
        } else {
            return null;
        }
    }

    public static String paramSourceToString(SqlParameterSource paramSource) {
        if (paramSource != null
            && paramSource.getParameterNames() != null
            && paramSource.getParameterNames().length > 0) {

            StringJoiner joiner = new StringJoiner(", ", "{", "}");
            for (String paramName : paramSource.getParameterNames()) {
                addParam(joiner, paramName, paramSource.getValue(paramName));
            }
            return joiner.toString();
        } else {
            return null;
        }
    }

    public static void addParam(StringJoiner joiner, String paramName, Object value) {
        if (value instanceof SqlLobValue) {
            joiner.add(paramName + "=<lob-value>");
        } else {
            joiner.add(paramName + "=" + value);
        }
    }
}
