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

package cafe.jeffrey.jfr.events.mybatis;

import cafe.jeffrey.jfr.events.trace.AttributeValues;
import cafe.jeffrey.jfr.events.trace.SpanAttributes;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.session.Configuration;

import java.io.InputStream;
import java.io.Reader;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLXML;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The values MyBatis bound into a statement, as the JSON object the event's {@code params} field
 * expects.
 * <p>
 * Resolution follows MyBatis' own {@code DefaultParameterHandler}, which is the only way to arrive
 * at the values that were actually bound: an additional parameter wins, a parameter object the type
 * handlers know is itself the value, and anything else is read off the parameter object as a
 * property. Because the statement has already run by the time this is asked for, every property
 * here is one MyBatis resolved a moment ago.
 */
final class StatementParameters {

    /** Content that must not be rendered: a stream would be consumed by reading it. */
    private static final List<Class<?>> LOB_TYPES =
            List.of(byte[].class, Blob.class, Clob.class, InputStream.class, Reader.class, SQLXML.class);

    private static final String LOB_PLACEHOLDER = "<lob-value>";
    private StatementParameters() {
    }

    /**
     * @return the bound values as a JSON object, or {@code null} for a statement that takes none —
     * an absent field reads better than an empty object in the dashboard
     */
    static String json(MappedStatement statement, BoundSql boundSql, int maxValueLength) {
        List<ParameterMapping> mappings = boundSql.getParameterMappings();
        if (mappings.isEmpty()) {
            return null;
        }

        Configuration configuration = statement.getConfiguration();
        Object parameterObject = boundSql.getParameterObject();
        SpanAttributes attributes = SpanAttributes.create();
        Set<String> written = new HashSet<>();

        for (ParameterMapping mapping : mappings) {
            if (mapping.getMode() == ParameterMode.OUT) {
                // An OUT parameter carries no input value; it exists to receive one.
                continue;
            }
            String property = mapping.getProperty();
            // The same property twice ("a = #{id} OR b = #{id}") resolves to the same value both
            // times, and SpanAttributes writes keys in the order given without de-duplicating them.
            if (!written.add(property)) {
                continue;
            }
            put(attributes, property, resolve(configuration, boundSql, parameterObject, property), maxValueLength);
        }

        return written.isEmpty() ? null : attributes.json();
    }

    private static Object resolve(
            Configuration configuration, BoundSql boundSql, Object parameterObject, String property) {

        if (boundSql.hasAdditionalParameter(property)) {
            return boundSql.getAdditionalParameter(property);
        }
        if (parameterObject == null) {
            return null;
        }
        if (configuration.getTypeHandlerRegistry().hasTypeHandler(parameterObject.getClass())) {
            // The statement takes a single scalar, and the property is the name it was given.
            return parameterObject;
        }
        return configuration.newMetaObject(parameterObject).getValue(property);
    }

    /**
     * Content that would have to be read to be rendered is named rather than read; everything else
     * is written the way every other emitter writes a captured value.
     */
    private static void put(SpanAttributes attributes, String key, Object value, int maxValueLength) {
        if (LOB_TYPES.stream().anyMatch(type -> type.isInstance(value))) {
            attributes.put(key, LOB_PLACEHOLDER);
            return;
        }

        AttributeValues.put(attributes, key, value, maxValueLength);
    }
}
