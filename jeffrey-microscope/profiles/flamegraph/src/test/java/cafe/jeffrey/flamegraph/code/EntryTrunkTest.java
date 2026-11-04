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


package cafe.jeffrey.flamegraph.code;

import cafe.jeffrey.flamegraph.code.EntryTrunk.Entry;
import cafe.jeffrey.flamegraph.code.EntryTrunk.Frame;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntryTrunkTest {

    private static final Frame THREAD = library("java.lang.Thread", "run");
    private static final Frame POOL = library("java.util.concurrent.ThreadPoolExecutor", "runWorker");
    private static final Frame WORKER_LOOP = own("com.acme.App", "lambda$main$1");
    private static final Frame OWN_FILTER = own("com.acme.web.AuditFilter", "doFilter");
    private static final Frame DISPATCH = library("org.springframework.web.servlet.DispatcherServlet", "doDispatch");
    private static final Frame INVOKE = library("org.springframework.web.method.support.InvocableHandlerMethod",
            "invoke");
    private static final Frame DISPATCH_LAMBDA = own("com.acme.App", "lambda$main$0");
    private static final Frame CONTROLLER = own("com.acme.web.OrderController", "history");
    private static final Frame SERVICE = own("com.acme.OrderService", "history");
    private static final Frame FIND_ORDERS = own("com.acme.OrderRepository", "findOrders");
    private static final Frame FIND_LINES = own("com.acme.OrderRepository", "findLines");
    private static final Frame TEMPLATE = library("org.springframework.jdbc.core.JdbcTemplate", "query");
    private static final Frame CALLBACK = own("com.acme.OrderRepository", "lambda$findLines$0");

    private static Frame own(String className, String methodName) {
        return new Frame(new MethodKey(className, methodName), true);
    }

    private static Frame library(String className, String methodName) {
        return new Frame(new MethodKey(className, methodName), false);
    }

    private static Optional<MethodKey> entryOf(EntryTrunk trunk) {
        return trunk.entry().map(Entry::method);
    }

    @Nested
    class TheHandler {

        @Test
        void isTheFirstOwnFrameAfterTheFrameworksDispatchNotAnOwnFilterAboveIt() {
            EntryTrunk trunk = new EntryTrunk();
            trunk.add(List.of(THREAD, OWN_FILTER, DISPATCH, INVOKE, CONTROLLER, SERVICE, FIND_ORDERS), 10);
            trunk.add(List.of(THREAD, OWN_FILTER, DISPATCH, INVOKE, CONTROLLER, SERVICE, FIND_LINES), 90);

            assertEquals(Optional.of(new Entry(CONTROLLER.method(), 100)), trunk.entry());
        }

        @Test
        void isNotTheWorkerLoopNorTheLambdaThatDispatchesToIt() {
            EntryTrunk trunk = new EntryTrunk();
            trunk.add(List.of(THREAD, POOL, WORKER_LOOP, DISPATCH, DISPATCH_LAMBDA, CONTROLLER, SERVICE, FIND_ORDERS),
                    1);
            trunk.add(List.of(THREAD, POOL, WORKER_LOOP, DISPATCH, DISPATCH_LAMBDA, CONTROLLER, SERVICE, FIND_LINES),
                    11);

            assertEquals(Optional.of(CONTROLLER.method()), entryOf(trunk));
        }

        @Test
        void isNotACallbackTheCodeHandsALibraryBelowIt() {
            EntryTrunk trunk = new EntryTrunk();
            trunk.add(List.of(THREAD, DISPATCH, CONTROLLER, SERVICE, FIND_LINES, TEMPLATE, CALLBACK), 100);

            assertEquals(Optional.of(CONTROLLER.method()), entryOf(trunk));
        }

        @Test
        void isALambdaWhenTheHandlerItselfIsOne() {
            EntryTrunk trunk = new EntryTrunk();
            trunk.add(List.of(THREAD, DISPATCH, DISPATCH_LAMBDA), 5);

            assertEquals(Optional.of(DISPATCH_LAMBDA.method()), entryOf(trunk));
        }
    }

    @Nested
    class TheTrunk {

        @Test
        void ignoresAFewStacksThatTookAnotherPath() {
            EntryTrunk trunk = new EntryTrunk();
            trunk.add(List.of(THREAD, DISPATCH, CONTROLLER, SERVICE), 95);
            trunk.add(List.of(THREAD, POOL, WORKER_LOOP, SERVICE), 5);

            assertEquals(Optional.of(new Entry(CONTROLLER.method(), 95)), trunk.entry());
        }

        @Test
        void hasNoEntryWhenTheStacksPartBeforeReachingOwnCode() {
            EntryTrunk trunk = new EntryTrunk();
            trunk.add(List.of(THREAD, DISPATCH, CONTROLLER), 50);
            trunk.add(List.of(THREAD, POOL, WORKER_LOOP), 50);

            assertEquals(Optional.empty(), trunk.entry());
        }

        @Test
        void hasNoEntryWithoutStacks() {
            assertEquals(Optional.empty(), new EntryTrunk().entry());
        }
    }
}
