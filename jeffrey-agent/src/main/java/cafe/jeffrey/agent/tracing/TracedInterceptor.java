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

package cafe.jeffrey.agent.tracing;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * What a woven {@code @Traced} method calls instead of its own body.
 * <p>
 * This is the only agent class the instrumented code links against, which is why it must stay
 * public and why it deliberately knows nothing about tracing: it hands the method's identity, its
 * arguments and its original body to the library's runtime, and that runtime decides what a span
 * is. When there is no runtime to hand them to, it calls the body and the method behaves as if the
 * agent were not attached.
 * <p>
 * The call reaches here because the agent jar sits on the system class path, which every ordinary
 * class loader delegates to as its parent — including Spring Boot's. A container that refuses to
 * delegate would fail to link this class instead, which is a documented limitation rather than
 * something the agent can work around.
 */
public final class TracedInterceptor {

    private TracedInterceptor() {
    }

    /**
     * @param method    the woven method, cached by ByteBuddy in the instrumented class
     * @param arguments the values it was called with
     * @param body      its original body, moved aside by the rebase
     */
    @RuntimeType
    public static Object intercept(
            @Origin Method method,
            @AllArguments Object[] arguments,
            @SuperCall Callable<Object> body) throws Throwable {

        MethodHandle traced = TracedRuntimeBinding.forClass(method.getDeclaringClass());
        if (traced == null) {
            return body.call();
        }

        // invokeExact, not Method#invoke: a method handle propagates whatever the body threw as
        // itself, so a caller catches the exception its method declared rather than a wrapper.
        return (Object) traced.invokeExact(method, arguments, body);
    }
}
