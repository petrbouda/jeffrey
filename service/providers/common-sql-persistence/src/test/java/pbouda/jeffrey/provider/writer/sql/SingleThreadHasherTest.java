/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.provider.writer.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.provider.api.model.EventFrame;
import pbouda.jeffrey.provider.api.model.EventThread;

import static org.junit.jupiter.api.Assertions.*;

class SingleThreadHasherTest {

    private SingleThreadHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new SingleThreadHasher();
    }

    @Nested
    class HashFrame {

        @Test
        void sameFrameProducesSameHash() {
            EventFrame frame = new EventFrame("com.example.MyClass", "doWork", "JIT compiled", 10, 42);

            long hash1 = hasher.hashFrame("profile1", frame);
            long hash2 = hasher.hashFrame("profile1", frame);

            assertEquals(hash1, hash2);
        }

        @Test
        void differentClassProducesDifferentHash() {
            EventFrame frame1 = new EventFrame("com.example.MyClass", "doWork", "JIT compiled", 10, 42);
            EventFrame frame2 = new EventFrame("com.example.OtherClass", "doWork", "JIT compiled", 10, 42);

            long hash1 = hasher.hashFrame("profile1", frame1);
            long hash2 = hasher.hashFrame("profile1", frame2);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void differentMethodProducesDifferentHash() {
            EventFrame frame1 = new EventFrame("com.example.MyClass", "doWork", "JIT compiled", 10, 42);
            EventFrame frame2 = new EventFrame("com.example.MyClass", "doOtherWork", "JIT compiled", 10, 42);

            long hash1 = hasher.hashFrame("profile1", frame1);
            long hash2 = hasher.hashFrame("profile1", frame2);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void differentTypeProducesDifferentHash() {
            EventFrame frame1 = new EventFrame("com.example.MyClass", "doWork", "JIT compiled", 10, 42);
            EventFrame frame2 = new EventFrame("com.example.MyClass", "doWork", "Interpreted", 10, 42);

            long hash1 = hasher.hashFrame("profile1", frame1);
            long hash2 = hasher.hashFrame("profile1", frame2);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void differentLineProducesDifferentHash() {
            EventFrame frame1 = new EventFrame("com.example.MyClass", "doWork", "JIT compiled", 10, 42);
            EventFrame frame2 = new EventFrame("com.example.MyClass", "doWork", "JIT compiled", 10, 100);

            long hash1 = hasher.hashFrame("profile1", frame1);
            long hash2 = hasher.hashFrame("profile1", frame2);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void differentBciProducesDifferentHash() {
            EventFrame frame1 = new EventFrame("com.example.MyClass", "doWork", "JIT compiled", 10, 42);
            EventFrame frame2 = new EventFrame("com.example.MyClass", "doWork", "JIT compiled", 20, 42);

            long hash1 = hasher.hashFrame("profile1", frame1);
            long hash2 = hasher.hashFrame("profile1", frame2);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void differentProfileIdProducesDifferentHash() {
            EventFrame frame = new EventFrame("com.example.MyClass", "doWork", "JIT compiled", 10, 42);

            long hash1 = hasher.hashFrame("profile1", frame);
            long hash2 = hasher.hashFrame("profile2", frame);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void nullValuesHandledGracefully() {
            EventFrame frame = new EventFrame(null, null, null, 0, 0);

            long hash = hasher.hashFrame("profile1", frame);

            assertNotEquals(0, hash);
        }

        @Test
        void emptyStringsHandledGracefully() {
            EventFrame frame = new EventFrame("", "", "", 0, 0);

            long hash = hasher.hashFrame("profile1", frame);

            assertNotEquals(0, hash);
        }

        @Test
        void lengthPrefixPreventsCollisions() {
            // "AB" + "CD" vs "A" + "BCD" - without length prefix these would hash the same
            EventFrame frame1 = new EventFrame("AB", "CD", "type", 0, 0);
            EventFrame frame2 = new EventFrame("A", "BCD", "type", 0, 0);

            long hash1 = hasher.hashFrame("profile1", frame1);
            long hash2 = hasher.hashFrame("profile1", frame2);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void bufferGrowsForLargeFrames() {
            String largeClass = "a".repeat(5000);
            String largeMethod = "b".repeat(5000);
            EventFrame frame = new EventFrame(largeClass, largeMethod, "JIT compiled", 10, 42);

            long hash = hasher.hashFrame("profile1", frame);

            assertNotEquals(0, hash);
        }
    }

    @Nested
    class HashStackTrace {

        @Test
        void nullArrayReturnsZero() {
            long hash = hasher.hashStackTrace("profile1", null);

            assertEquals(0L, hash);
        }

        @Test
        void emptyArrayReturnsZero() {
            long hash = hasher.hashStackTrace("profile1", new long[0]);

            assertEquals(0L, hash);
        }

        @Test
        void sameFrameHashesProduceSameStackTraceHash() {
            long[] frames = {123L, 456L, 789L};

            long hash1 = hasher.hashStackTrace("profile1", frames);
            long hash2 = hasher.hashStackTrace("profile1", frames);

            assertEquals(hash1, hash2);
        }

        @Test
        void differentFrameOrderProducesDifferentHash() {
            long[] frames1 = {123L, 456L, 789L};
            long[] frames2 = {789L, 456L, 123L};

            long hash1 = hasher.hashStackTrace("profile1", frames1);
            long hash2 = hasher.hashStackTrace("profile1", frames2);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void differentProfileIdProducesDifferentHash() {
            long[] frames = {123L, 456L, 789L};

            long hash1 = hasher.hashStackTrace("profile1", frames);
            long hash2 = hasher.hashStackTrace("profile2", frames);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void singleFrameStackTrace() {
            long[] frames = {12345L};

            long hash = hasher.hashStackTrace("profile1", frames);

            assertNotEquals(0L, hash);
        }

        @Test
        void largeStackTrace() {
            long[] frames = new long[1000];
            for (int i = 0; i < frames.length; i++) {
                frames[i] = i * 100L;
            }

            long hash = hasher.hashStackTrace("profile1", frames);

            assertNotEquals(0L, hash);
        }
    }

    @Nested
    class HashThread {

        @Test
        void sameThreadProducesSameHash() {
            EventThread thread = new EventThread("main", 100L, 1L, false);

            long hash1 = hasher.hashThread("profile1", thread);
            long hash2 = hasher.hashThread("profile1", thread);

            assertEquals(hash1, hash2);
        }

        @Test
        void differentNameProducesDifferentHash() {
            EventThread thread1 = new EventThread("main", 100L, 1L, false);
            EventThread thread2 = new EventThread("worker", 100L, 1L, false);

            long hash1 = hasher.hashThread("profile1", thread1);
            long hash2 = hasher.hashThread("profile1", thread2);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void differentOsIdProducesDifferentHash() {
            EventThread thread1 = new EventThread("main", 100L, 1L, false);
            EventThread thread2 = new EventThread("main", 200L, 1L, false);

            long hash1 = hasher.hashThread("profile1", thread1);
            long hash2 = hasher.hashThread("profile1", thread2);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void differentJavaIdProducesDifferentHash() {
            EventThread thread1 = new EventThread("main", 100L, 1L, false);
            EventThread thread2 = new EventThread("main", 100L, 2L, false);

            long hash1 = hasher.hashThread("profile1", thread1);
            long hash2 = hasher.hashThread("profile1", thread2);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void differentProfileIdProducesDifferentHash() {
            EventThread thread = new EventThread("main", 100L, 1L, false);

            long hash1 = hasher.hashThread("profile1", thread);
            long hash2 = hasher.hashThread("profile2", thread);

            assertNotEquals(hash1, hash2);
        }

        @Test
        void nullOsIdHandledAsZero() {
            EventThread thread1 = new EventThread("main", null, 1L, false);
            EventThread thread2 = new EventThread("main", 0L, 1L, false);

            long hash1 = hasher.hashThread("profile1", thread1);
            long hash2 = hasher.hashThread("profile1", thread2);

            assertEquals(hash1, hash2);
        }

        @Test
        void nullJavaIdHandledAsZero() {
            EventThread thread1 = new EventThread("main", 100L, null, false);
            EventThread thread2 = new EventThread("main", 100L, 0L, false);

            long hash1 = hasher.hashThread("profile1", thread1);
            long hash2 = hasher.hashThread("profile1", thread2);

            assertEquals(hash1, hash2);
        }

        @Test
        void nullNameHandledAsEmptyString() {
            EventThread thread = new EventThread(null, 100L, 1L, false);

            long hash = hasher.hashThread("profile1", thread);

            assertNotEquals(0L, hash);
        }

        @Test
        void virtualThreadFlagNotAffectingHash() {
            // Note: isVirtual is not included in hash calculation
            EventThread thread1 = new EventThread("main", 100L, 1L, false);
            EventThread thread2 = new EventThread("main", 100L, 1L, true);

            long hash1 = hasher.hashThread("profile1", thread1);
            long hash2 = hasher.hashThread("profile1", thread2);

            // Both should produce the same hash since isVirtual is not hashed
            assertEquals(hash1, hash2);
        }
    }

    @Nested
    class CrossMethodConsistency {

        @Test
        void integratedStackTraceHashingWithFrames() {
            EventFrame frame1 = new EventFrame("com.example.A", "method1", "JIT compiled", 0, 10);
            EventFrame frame2 = new EventFrame("com.example.B", "method2", "JIT compiled", 0, 20);
            EventFrame frame3 = new EventFrame("com.example.C", "method3", "JIT compiled", 0, 30);

            long frameHash1 = hasher.hashFrame("profile1", frame1);
            long frameHash2 = hasher.hashFrame("profile1", frame2);
            long frameHash3 = hasher.hashFrame("profile1", frame3);

            long[] frameHashes = {frameHash1, frameHash2, frameHash3};
            long stackTraceHash = hasher.hashStackTrace("profile1", frameHashes);

            assertNotEquals(0L, stackTraceHash);
            assertNotEquals(frameHash1, stackTraceHash);
            assertNotEquals(frameHash2, stackTraceHash);
            assertNotEquals(frameHash3, stackTraceHash);
        }
    }
}
