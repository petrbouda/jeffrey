/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package io.jafar.parser;

import io.jafar.parser.internal_api.DeserializationHandler;
import io.jafar.parser.internal_api.RecordingStream;

import java.lang.invoke.MethodHandle;

public final class JFRValueDeserializer<T> implements DeserializationHandler<T> {
    private final Class<T> clazz;
    private MethodHandle handler = null;

    private JFRValueDeserializer(Class<T> clazz) {
        this.clazz = clazz;
    }

    public static <T> JFRValueDeserializer<T> create(Class<T> clazz) {
        return new JFRValueDeserializer<>(clazz);
    }

    public void setHandler(MethodHandle handler) {
        this.handler = handler;
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public JFRValueDeserializer<T> duplicate() {
        return new JFRValueDeserializer<>(clazz);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T handle(RecordingStream stream) {
        try {
            T value = handler != null ? (T) handler.invoke(stream) : null;
            return value;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
