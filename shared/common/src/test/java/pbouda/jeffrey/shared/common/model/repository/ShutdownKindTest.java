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

package pbouda.jeffrey.shared.common.model.repository;

import org.junit.jupiter.api.Test;
import pbouda.jeffrey.shared.common.model.repository.InstanceEnvironment.ShutdownKind;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShutdownKindTest {

    @Test
    void gracefulReasonFromJvmCpp() {
        assertEquals(ShutdownKind.GRACEFUL, ShutdownKind.classify("Shutdown requested from Java"));
    }

    @Test
    void vmErrorFromFatalCrashPath() {
        assertEquals(ShutdownKind.VM_ERROR, ShutdownKind.classify("VM Error"));
    }

    @Test
    void crashOomFromJdk24Plus() {
        assertEquals(ShutdownKind.CRASH_OOM, ShutdownKind.classify("CrashOnOutOfMemoryError"));
    }

    @Test
    void thirdPartyReasonFallsToUnknown() {
        assertEquals(ShutdownKind.UNKNOWN, ShutdownKind.classify("SapMachine custom reason"));
    }

    @Test
    void caseMismatchIsUnknown() {
        // HotSpot writes exactly "VM Error"; any other casing is not one we trust.
        assertEquals(ShutdownKind.UNKNOWN, ShutdownKind.classify("vm error"));
    }

    @Test
    void nullIsUnknown() {
        assertEquals(ShutdownKind.UNKNOWN, ShutdownKind.classify(null));
    }

    @Test
    void emptyStringIsUnknown() {
        assertEquals(ShutdownKind.UNKNOWN, ShutdownKind.classify(""));
    }
}
