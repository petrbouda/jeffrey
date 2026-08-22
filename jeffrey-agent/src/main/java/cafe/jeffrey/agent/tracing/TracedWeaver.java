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

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.utility.JavaModule;

import java.lang.System.Logger.Level;
import java.lang.instrument.Instrumentation;

import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isBridge;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isNative;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.declaresMethod;
import static net.bytebuddy.matcher.ElementMatchers.not;

/**
 * Rewrites every {@code @Traced} method so it runs inside a span.
 * <p>
 * The annotation is matched <b>by name</b>, never by class: the agent must decide whether to weave
 * a class without loading anything the application owns, and it has no compile-time knowledge of
 * {@code jeffrey-events} at all. ByteBuddy reads the annotation out of the class file for exactly
 * this reason.
 * <p>
 * The rewrite is a <em>rebase</em>: the original body moves to a synthetic method and the original
 * signature is given a new body that hands that method to {@link TracedInterceptor} as a
 * {@link java.util.concurrent.Callable}. It has to be that shape — a span is bound with a
 * {@code ScopedValue}, whose binding is structured, so the body must run <em>inside</em> the call
 * that opens the span rather than between a pair of enter and exit hooks.
 * <p>
 * Weaving happens as classes load, which is why the agent belongs on the command line rather than
 * attached later: a class already loaded when the agent starts keeps its original bodies.
 */
public final class TracedWeaver {

    private static final System.Logger LOG = System.getLogger(TracedWeaver.class.getName());

    private static final String TRACED_ANNOTATION = "cafe.jeffrey.jfr.events.trace.Traced";

    /** The release the tracing runtime is compiled for; below it, nothing could ever be recorded. */
    private static final int REQUIRED_JAVA_VERSION = 25;

    private TracedWeaver() {
    }

    /**
     * Installs the weaver when it was asked for and could do anything.
     *
     * @return whether the weaver was installed
     */
    public static boolean install(boolean tracingEnabled, Instrumentation instrumentation) {
        return installTransformer(tracingEnabled, instrumentation) != null;
    }

    /**
     * @return the installed transformer, or {@code null} when the weaver did not install. Tests use
     * it to leave the JVM as they found it; the agent itself has nothing to undo.
     */
    static ResettableClassFileTransformer installTransformer(
            boolean tracingEnabled, Instrumentation instrumentation) {

        if (!shouldInstall(tracingEnabled, Runtime.version().feature())) {
            return null;
        }

        // ByteBuddy's own ignore set (its classes, JDK reflection internals, synthetic types) is
        // deliberately left in place: overriding it removes those guards, and there is nothing to
        // add — a class without an annotated method cannot match, the agent's own classes included.
        return new AgentBuilder.Default()
                .assureReadEdgeFromAndTo(instrumentation, TracedInterceptor.class)
                .with(new WeavingListener())
                .type(declaresMethod(isAnnotatedWith(named(TRACED_ANNOTATION))).and(not(isInterface())))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder
                        .method(isMethod()
                                .and(isAnnotatedWith(named(TRACED_ANNOTATION)))
                                .and(not(isAbstract()))
                                .and(not(isNative()))
                                .and(not(isBridge())))
                        .intercept(MethodDelegation.to(TracedInterceptor.class)))
                .installOn(instrumentation);
    }

    /**
     * Below Java 25 the tracing runtime cannot load, so installing a transformer consulted on every
     * class load would buy an application nothing but the cost of consulting it.
     */
    static boolean shouldInstall(boolean tracingEnabled, int javaFeatureVersion) {
        if (!tracingEnabled) {
            LOG.log(Level.INFO, "Method tracing is disabled, no classes will be instrumented");
            return false;
        }
        if (javaFeatureVersion < REQUIRED_JAVA_VERSION) {
            LOG.log(Level.INFO, "Method tracing needs Java " + REQUIRED_JAVA_VERSION
                    + " and this JVM is " + javaFeatureVersion + ", skipping instrumentation");
            return false;
        }

        return true;
    }

    /**
     * A class that cannot be woven must not be a class that cannot be loaded, so failures are
     * reported and the original class file is used unchanged.
     */
    private static final class WeavingListener extends AgentBuilder.Listener.Adapter {

        @Override
        public void onError(
                String typeName, ClassLoader classLoader, JavaModule module, boolean loaded, Throwable throwable) {

            LOG.log(Level.WARNING, "Failed to instrument traced methods, class left unchanged: "
                    + typeName + " (" + throwable + ")");
        }

        @Override
        public void onTransformation(
                TypeDescription type, ClassLoader classLoader, JavaModule module,
                boolean loaded, net.bytebuddy.dynamic.DynamicType dynamicType) {

            LOG.log(Level.DEBUG, "Instrumented traced methods: " + type.getName());
        }
    }
}
