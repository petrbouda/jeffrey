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

package cafe.jeffrey.jfr.events.mybatis;

/**
 * What the interceptor records about a statement beyond its identity.
 * <p>
 * Parameter capture defaults to <b>on</b>, unlike the HTTP filter's capture flags. The two cases
 * are not the same: a query string is free-form user input that happens to travel with a request,
 * while a statement's parameters are the statement — the reason one call out of thousands with the
 * same SQL was the slow one, and what Jeffrey's Database dashboard has a column for. Recording the
 * SQL and hiding what it ran with makes the slow statement unreadable.
 * <p>
 * That said, the values are recorded verbatim and a recording is a file that gets uploaded, shared
 * and kept. An application whose mappers take e-mail addresses, tokens or anything else it would
 * not paste into a bug report turns capture off:
 *
 * <pre>{@code
 * new JeffreyMyBatisInterceptor(MyBatisStatementSettings.noParameters());
 * // Spring Boot: jeffrey.tracing.mybatis-capture-parameters=false
 * }</pre>
 *
 * @param captureParameters record the bound parameter values as a JSON object on the event
 */
public record MyBatisStatementSettings(boolean captureParameters) {

    /**
     * Records parameters, each value as it was bound. Content that would have to be read to be
     * rendered — a {@code Clob}, a stream — is still named rather than read.
     */
    public static MyBatisStatementSettings defaults() {
        return new MyBatisStatementSettings(true);
    }

    /**
     * Records the statement and its name, and nothing about what it ran with.
     */
    public static MyBatisStatementSettings noParameters() {
        return new MyBatisStatementSettings(false);
    }
}
