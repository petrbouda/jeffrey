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

package cafe.jeffrey.jfr.events.tracing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the two surefire runs this module's tests go through: the default run with both storages,
 * where the Tracer picks the ScopedValue one wherever the JVM can load it, and the
 * {@code thread-local-storage} run without it. If the exclusion stopped applying, both runs would
 * silently test the same storage.
 */
class StorageRunTest {

    private static final String SCOPED_VALUE_STORAGE =
            "cafe.jeffrey.jfr.events.trace.scoped.ScopedValueSpanContextStorage";
    private static final String THREAD_LOCAL_STORAGE =
            "cafe.jeffrey.jfr.events.trace.threadlocal.ThreadLocalSpanContextStorage";
    private static final String EXPECT_SCOPED_VALUE = "jeffrey.test.scoped-value-storage";
    private static final int SCOPED_VALUE_FEATURE_RELEASE = 25;

    @Test
    @DisplayName("the ScopedValue storage loads exactly in the run that expects it, on a JVM that can")
    void scopedValueStorageMatchesTheRun() {
        boolean expected = Boolean.parseBoolean(System.getProperty(EXPECT_SCOPED_VALUE, "true"))
                && Runtime.version().feature() >= SCOPED_VALUE_FEATURE_RELEASE;

        assertEquals(expected, isLoadable(SCOPED_VALUE_STORAGE));
    }

    @Test
    @DisplayName("the ThreadLocal storage loads in every run")
    void threadLocalStorageAlwaysLoads() {
        assertTrue(isLoadable(THREAD_LOCAL_STORAGE));
    }

    private static boolean isLoadable(String className) {
        try {
            Class.forName(className, false, StorageRunTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
