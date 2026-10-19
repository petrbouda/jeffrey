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

package cafe.jeffrey.profile.common.exceptions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The same cases as {@code throwingFrameIndex} in {@code traceStackFolding.test.ts}: both readings of
 * the rule have to name the same frame.
 */
class ThrowFramesTest {

    private static final String IO_EXCEPTION = "java.io.IOException";

    private static ThrowFrame frame(String className, String methodName, int line) {
        return new ThrowFrame(className, methodName, line);
    }

    // The shape JFR produces, because the throw event is emitted from inside Throwable's
    // constructor: one <init> per level of the exception's own hierarchy, then the real frame.
    private static final List<ThrowFrame> OGNL = List.of(
            frame("java.lang.Throwable", "<init>", 266),
            frame("java.lang.Exception", "<init>", 113),
            frame(IO_EXCEPTION, "<init>", 62),
            frame("org.apache.ibatis.ognl.JavaCharStream", "fillBuff", 148),
            frame("com.acme.reports.ReportEventsMapper", "getReportEvents", 88),
            frame("java.lang.Thread", "run", 1583));

    @Nested
    @DisplayName("throwingFrameIndex")
    class ThrowingFrameIndex {

        @Test
        void pointsPastTheConstructorChain() {
            assertEquals(3, ThrowFrames.throwingFrameIndex(OGNL, IO_EXCEPTION));
        }

        @Test
        void absorbsFillInStackTrace() {
            List<ThrowFrame> withFill = new ArrayList<>();
            withFill.add(frame("java.lang.Throwable", "fillInStackTrace", 1072));
            withFill.addAll(OGNL);

            assertEquals(4, ThrowFrames.throwingFrameIndex(withFill, IO_EXCEPTION));
        }

        @Test
        void keepsAnInitThatIsTheRealAnswer() {
            List<ThrowFrame> inConstructor = List.of(
                    frame("java.lang.Throwable", "<init>", 266),
                    frame("java.lang.Exception", "<init>", 113),
                    frame("com.acme.MyException", "<init>", 12),
                    frame("com.acme.Foo", "<init>", 40),
                    frame("java.lang.Thread", "run", 1583));

            assertEquals(3, ThrowFrames.throwingFrameIndex(inConstructor, "com.acme.MyException"));
        }

        @Test
        void staysAtZeroWhenTheStackDoesNotHaveTheShape() {
            assertEquals(0, ThrowFrames.throwingFrameIndex(OGNL, "com.acme.SomethingElse"));
            assertEquals(0, ThrowFrames.throwingFrameIndex(OGNL, null));
            assertEquals(0, ThrowFrames.throwingFrameIndex(List.of(), IO_EXCEPTION));
        }

        @Test
        void staysAtZeroWhenTheWholeStackIsTheChain() {
            List<ThrowFrame> nothingElse = List.of(
                    frame("java.lang.Throwable", "<init>", 266),
                    frame(IO_EXCEPTION, "<init>", 62));

            assertEquals(0, ThrowFrames.throwingFrameIndex(nothingElse, IO_EXCEPTION));
        }

        @Test
        void endsTheChainAtTheLastFrameNamingTheThrownClass() {
            List<ThrowFrame> repeated = List.of(
                    frame("java.lang.Throwable", "<init>", 266),
                    frame("com.acme.MyException", "<init>", 12),
                    frame("com.acme.MyException", "<init>", 18),
                    frame("com.acme.Service", "call", 40));

            assertEquals(3, ThrowFrames.throwingFrameIndex(repeated, "com.acme.MyException"));
        }
    }

    @Nested
    @DisplayName("throwingFrame")
    class ThrowingFrame {

        @Test
        void returnsTheFrameAtTheIndex() {
            assertEquals(Optional.of(OGNL.get(3)), ThrowFrames.throwingFrame(OGNL, IO_EXCEPTION));
        }

        @Test
        void isEmptyForAnEmptyStack() {
            assertEquals(Optional.empty(), ThrowFrames.throwingFrame(List.of(), IO_EXCEPTION));
        }
    }
}
