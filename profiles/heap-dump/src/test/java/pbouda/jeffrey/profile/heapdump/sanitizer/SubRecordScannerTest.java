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

package pbouda.jeffrey.profile.heapdump.sanitizer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class SubRecordScannerTest {

    private static final int ID_SIZE = 8;

    @Nested
    class SingleSubRecords {

        @Test
        void scansRootUnknown() {
            // ROOT_UNKNOWN: tag(1) + id(8)
            ByteBuffer buf = ByteBuffer.allocate(9);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) HprofSubRecordTag.ROOT_UNKNOWN.value());
            buf.putLong(100L);
            buf.flip();

            SubRecordScanner.ScanResult result = SubRecordScanner.scan(buf, 0, buf.limit(), ID_SIZE);
            assertEquals(9, result.validBytes());
            assertEquals(1, result.subRecordCount());
            assertFalse(result.truncated());
        }

        @Test
        void scansRootStickyClass() {
            // ROOT_STICKY_CLASS: tag(1) + id(8)
            ByteBuffer buf = ByteBuffer.allocate(9);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) HprofSubRecordTag.ROOT_STICKY_CLASS.value());
            buf.putLong(200L);
            buf.flip();

            SubRecordScanner.ScanResult result = SubRecordScanner.scan(buf, 0, buf.limit(), ID_SIZE);
            assertEquals(9, result.validBytes());
            assertEquals(1, result.subRecordCount());
            assertFalse(result.truncated());
        }

        @Test
        void scansRootJniGlobal() {
            // ROOT_JNI_GLOBAL: tag(1) + id(8) + id(8) = 17
            ByteBuffer buf = ByteBuffer.allocate(17);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) HprofSubRecordTag.ROOT_JNI_GLOBAL.value());
            buf.putLong(100L);
            buf.putLong(200L);
            buf.flip();

            SubRecordScanner.ScanResult result = SubRecordScanner.scan(buf, 0, buf.limit(), ID_SIZE);
            assertEquals(17, result.validBytes());
            assertEquals(1, result.subRecordCount());
            assertFalse(result.truncated());
        }

        @Test
        void scansInstanceDump() {
            // INSTANCE_DUMP: tag(1) + id(8) + u4(4) + id(8) + u4(bytesFollowing=4) + data(4)
            int dataSize = 4;
            int totalSize = 1 + ID_SIZE + 4 + ID_SIZE + 4 + dataSize;
            ByteBuffer buf = ByteBuffer.allocate(totalSize);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) HprofSubRecordTag.INSTANCE_DUMP.value());
            buf.putLong(1L);    // object ID
            buf.putInt(0);      // stack trace serial
            buf.putLong(2L);    // class object ID
            buf.putInt(dataSize); // bytes following
            buf.putInt(0xDEAD); // instance data
            buf.flip();

            SubRecordScanner.ScanResult result = SubRecordScanner.scan(buf, 0, buf.limit(), ID_SIZE);
            assertEquals(totalSize, result.validBytes());
            assertEquals(1, result.subRecordCount());
            assertFalse(result.truncated());
        }

        @Test
        void scansPrimArrayDump() {
            // PRIM_ARRAY_DUMP for byte array: tag(1) + id(8) + u4(4) + u4(numElements=3) + u1(type=BYTE) + data(3)
            byte[] elements = {1, 2, 3};
            int totalSize = 1 + ID_SIZE + 4 + 4 + 1 + elements.length;
            ByteBuffer buf = ByteBuffer.allocate(totalSize);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) HprofSubRecordTag.PRIM_ARRAY_DUMP.value());
            buf.putLong(10L);           // array object ID
            buf.putInt(0);              // stack trace serial
            buf.putInt(elements.length); // num elements
            buf.put((byte) HprofTypeSize.BYTE);
            buf.put(elements);
            buf.flip();

            SubRecordScanner.ScanResult result = SubRecordScanner.scan(buf, 0, buf.limit(), ID_SIZE);
            assertEquals(totalSize, result.validBytes());
            assertEquals(1, result.subRecordCount());
            assertFalse(result.truncated());
        }
    }

    @Nested
    class MultipleSubRecords {

        @Test
        void scansMultipleRecords() {
            HprofTestFileBuilder builder = new HprofTestFileBuilder().idSize(ID_SIZE);
            byte[] root1 = builder.buildRootUnknownSubRecord(1L);
            byte[] root2 = builder.buildRootStickyClassSubRecord(2L);
            byte[] root3 = builder.buildRootUnknownSubRecord(3L);
            byte[] combined = builder.concat(root1, root2, root3);

            ByteBuffer buf = ByteBuffer.wrap(combined);
            buf.order(ByteOrder.BIG_ENDIAN);

            SubRecordScanner.ScanResult result = SubRecordScanner.scan(buf, 0, combined.length, ID_SIZE);
            assertEquals(combined.length, result.validBytes());
            assertEquals(3, result.subRecordCount());
            assertFalse(result.truncated());
        }
    }

    @Nested
    class TruncatedData {

        @Test
        void detectsTruncatedSubRecord() {
            // Write a ROOT_UNKNOWN tag but truncate the ID
            ByteBuffer buf = ByteBuffer.allocate(5);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) HprofSubRecordTag.ROOT_UNKNOWN.value());
            buf.putInt(0); // only 4 bytes of ID, need 8
            buf.flip();

            SubRecordScanner.ScanResult result = SubRecordScanner.scan(buf, 0, buf.limit(), ID_SIZE);
            assertEquals(0, result.validBytes());
            assertEquals(0, result.subRecordCount());
            assertTrue(result.truncated());
        }

        @Test
        void recoversValidRecordsBeforeTruncation() {
            HprofTestFileBuilder builder = new HprofTestFileBuilder().idSize(ID_SIZE);
            byte[] validRoot = builder.buildRootUnknownSubRecord(1L);

            // Combine valid root + truncated data
            byte[] combined = new byte[validRoot.length + 3];
            System.arraycopy(validRoot, 0, combined, 0, validRoot.length);
            combined[validRoot.length] = (byte) HprofSubRecordTag.ROOT_UNKNOWN.value();
            combined[validRoot.length + 1] = 0;
            combined[validRoot.length + 2] = 0;

            ByteBuffer buf = ByteBuffer.wrap(combined);
            buf.order(ByteOrder.BIG_ENDIAN);

            SubRecordScanner.ScanResult result = SubRecordScanner.scan(buf, 0, combined.length, ID_SIZE);
            assertEquals(validRoot.length, result.validBytes());
            assertEquals(1, result.subRecordCount());
            assertTrue(result.truncated());
        }

        @Test
        void handlesUnknownTag() {
            ByteBuffer buf = ByteBuffer.allocate(10);
            buf.order(ByteOrder.BIG_ENDIAN);
            buf.put((byte) 0x99); // Unknown tag
            buf.putLong(0L);
            buf.flip();

            SubRecordScanner.ScanResult result = SubRecordScanner.scan(buf, 0, buf.limit(), ID_SIZE);
            assertEquals(0, result.validBytes());
            assertEquals(0, result.subRecordCount());
            assertTrue(result.truncated());
        }

        @Test
        void handlesEmptyBuffer() {
            ByteBuffer buf = ByteBuffer.allocate(0);
            SubRecordScanner.ScanResult result = SubRecordScanner.scan(buf, 0, 0, ID_SIZE);
            assertEquals(0, result.validBytes());
            assertEquals(0, result.subRecordCount());
            assertFalse(result.truncated());
        }
    }
}
