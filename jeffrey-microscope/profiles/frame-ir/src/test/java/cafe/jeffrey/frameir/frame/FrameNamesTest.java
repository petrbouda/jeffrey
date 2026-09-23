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
