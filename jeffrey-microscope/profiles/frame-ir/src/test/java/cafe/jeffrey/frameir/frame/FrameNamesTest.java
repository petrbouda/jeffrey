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

package cafe.jeffrey.frameir.frame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrameNamesTest {

    @Test
    void javaFrameUsesHashDelimiter() {
        assertEquals("org.springframework.web.filter.OncePerRequestFilter#doFilter",
                FrameNames.joinUnknown("org.springframework.web.filter.OncePerRequestFilter", "doFilter"));
    }

    @Test
    void cppFrameSeparatesModuleFromClassMethodWithHash() {
        // module '#' Class::method — the module becomes the "package" in the renderer.
        assertEquals("libjvm.so#CompileBroker::compiler_thread_loop",
                FrameNames.joinUnknown("libjvm.so", "CompileBroker::compiler_thread_loop"));
    }

    @Test
    void blankClassYieldsMethodOnly() {
        assertEquals("do_syscall_64", FrameNames.joinUnknown("", "do_syscall_64"));
        assertEquals("do_syscall_64", FrameNames.joinUnknown(null, "do_syscall_64"));
    }

    @Test
    void nativeLibraryStaysDottedNotHashed() {
        // libc.so.6 is a filename, not a Java class — keep it dotted so it isn't split as class "6".
        assertEquals("libc.so.6.clone3", FrameNames.joinUnknown("libc.so.6", "clone3"));
        assertEquals("libc.so.6.__new_sem_wait_slow64.constprop.0",
                FrameNames.joinUnknown("libc.so.6", "__new_sem_wait_slow64.constprop.0"));
    }

    @Test
    void goStyleLowercaseNameStaysDotted() {
        assertEquals("main.setFunctions.func7505",
                FrameNames.joinUnknown("main.setFunctions", "func7505"));
    }
}
