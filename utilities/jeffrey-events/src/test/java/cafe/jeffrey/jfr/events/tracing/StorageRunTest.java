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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the two surefire runs this module's tests go through - the default run with only the
 * ThreadLocal storage, and the {@code scoped-value-storage} run with only the ScopedValue one. If
 * an exclusion stopped applying, both runs would silently test the same storage, or both at once.
 */
class StorageRunTest {

    private static final String STORAGE_PACKAGE = "cafe.jeffrey.jfr.events.trace.";
    private static final List<String> STORAGES = List.of(
            "threadlocal.ThreadLocalSpanContextStorage",
            "scopedvalue.ScopedValueSpanContextStorage");
    private static final String EXPECTED_STORAGE = "jeffrey.test.expected-storage";

    @Test
    @DisplayName("exactly the storage this run is meant for is on the class path")
    void onlyTheExpectedStorageIsPresent() {
        List<String> present = STORAGES.stream()
                .filter(StorageRunTest::isPresent)
                .map(name -> name.substring(name.indexOf('.') + 1))
                .toList();

        assertEquals(List.of(System.getProperty(EXPECTED_STORAGE)), present);
    }

    private static boolean isPresent(String storage) {
        try {
            Class.forName(STORAGE_PACKAGE + storage, false, StorageRunTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
