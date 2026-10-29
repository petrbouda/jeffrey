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

import org.apache.ibatis.type.TypeHandlerRegistry;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Asks MyBatis whether a type handler is registered for a class, on every MyBatis line the
 * instrumentation runs under.
 * <p>
 * MyBatis 3.6 replaced {@code hasTypeHandler(Class)} with {@code hasTypeHandler(Type)}, and neither
 * line declares the other's method: a call compiled against one throws {@link NoSuchMethodError}
 * on the other. MyBatis is the application's dependency, not ours, so the method is looked up once,
 * by whichever of the two signatures the MyBatis on the classpath declares.
 */
final class TypeHandlerProbe {

    private static final String METHOD_NAME = "hasTypeHandler";

    /** Newest first: 3.6+ takes a {@link Type}, 3.5 a {@link Class}. */
    private static final List<Class<?>> PARAMETER_TYPES = List.of(Type.class, Class.class);

    private static final MethodType CALL_TYPE =
            MethodType.methodType(boolean.class, TypeHandlerRegistry.class, Class.class);

    private static final MethodHandle HAS_TYPE_HANDLER = resolve();

    private TypeHandlerProbe() {
    }

    static boolean hasTypeHandler(TypeHandlerRegistry registry, Class<?> type) {
        try {
            return (boolean) HAS_TYPE_HANDLER.invokeExact(registry, type);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("TypeHandlerRegistry.hasTypeHandler failed", e);
        }
    }

    private static MethodHandle resolve() {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        for (Class<?> parameterType : PARAMETER_TYPES) {
            MethodType declared = MethodType.methodType(boolean.class, parameterType);
            try {
                return lookup.findVirtual(TypeHandlerRegistry.class, METHOD_NAME, declared).asType(CALL_TYPE);
            } catch (NoSuchMethodException e) {
                // Not this MyBatis line; try the next signature.
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("TypeHandlerRegistry.hasTypeHandler is not accessible", e);
            }
        }
        throw new IllegalStateException(
                "Unsupported MyBatis version: TypeHandlerRegistry declares no hasTypeHandler(Type) or hasTypeHandler(Class)");
    }
}
