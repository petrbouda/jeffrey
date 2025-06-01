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
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.Enter;
import net.bytebuddy.asm.Advice.OnMethodExit;
import net.bytebuddy.asm.Advice.Return;
import net.bytebuddy.asm.Advice.This;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import pbouda.jeffrey.jfr.types.jdbc.JdbcQueryEvent;
import pbouda.jeffrey.provider.api.streamer.model.*;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static net.bytebuddy.asm.Advice.OnMethodEnter;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class JfrJdbcAgent {

    private static final String NAMED_PARAM_MAPPED_QUERY_SPEC_NAME =
            "org.springframework.jdbc.core.simple.DefaultJdbcClient$DefaultStatementSpec$NamedParamMappedQuerySpec";

    private static final String INDEXED_PARAM_MAPPED_QUERY_SPEC_NAME =
            "org.springframework.jdbc.core.simple.DefaultJdbcClient$DefaultStatementSpec$IndexedParamMappedQuerySpec";

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

        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(new AgentBuilder.Listener.WithErrorsOnly(AgentBuilder.Listener.StreamWriting.toSystemOut()))
                .with(new AgentBuilder.Listener.WithTransformationsOnly(AgentBuilder.Listener.StreamWriting.toSystemOut()))
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)

                .type(named(NAMED_PARAM_MAPPED_QUERY_SPEC_NAME).or(named(INDEXED_PARAM_MAPPED_QUERY_SPEC_NAME)))
                .transform((builder, _, _, _, _) ->
                        builder.visit(Advice.to(JdbcClientQueryAdvice.class).on(queryMatcher)))
                .installOn(inst);
    }

    public static class JdbcClientQueryAdvice {
        @OnMethodEnter
        static JdbcQueryEvent enter(@This Object thisObject) {
            JdbcQueryEvent event = new JdbcQueryEvent();

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
                event.rows = switch (result) {
                    case List<?> list -> list.size();
                    case null -> -1L; // very likely RowCallbackHandler was used
                    default -> 1L; // very likely ResultSetExtractor converting to single object was used
                };
                event.samples = event.rows;
                event.commit();
            }
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

    public static String paramSourceToString(SqlParameterSource paramSource) {
        if (paramSource.getParameterNames() != null && paramSource.getParameterNames().length > 0) {
            StringJoiner joiner = new StringJoiner(", ", "{", "}");
            for (String paramName : paramSource.getParameterNames()) {
                joiner.add(paramName + "=" + paramSource.getValue(paramName));
            }
            return joiner.toString();
        } else {
            return "{}";
        }
    }
}
