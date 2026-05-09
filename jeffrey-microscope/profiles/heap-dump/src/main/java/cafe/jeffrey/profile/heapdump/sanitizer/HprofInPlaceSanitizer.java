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

package cafe.jeffrey.profile.heapdump.sanitizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Sanitizes a corrupted HPROF file <strong>in place</strong> by applying the
 * {@link HprofRepair} operations produced by {@link HprofRepairPlanner}.
 *
 * <p>This is destructive: the original file is mutated. Use this when you don't
 * need to preserve the unrepaired bytes for forensics. Compared with the
 * copy-based sanitizer, this avoids a full file rewrite — the cost is constant
 * per detected corruption.
 */
public final class HprofInPlaceSanitizer {

    private static final Logger LOG = LoggerFactory.getLogger(HprofInPlaceSanitizer.class);

    private static final int RECORD_HEADER_SIZE = 9;

    private HprofInPlaceSanitizer() {
    }

    /**
     * Plans and applies repairs to {@code file} in place. Returns the diagnostic
     * result. If no repairs are needed the file is left untouched.
     */
    public static SanitizeResult sanitize(Path file) throws IOException {
        LOG.info("Starting in-place HPROF sanitization: file={}", file);

        HprofRepairPlan plan;
        try (FileChannel readChannel = FileChannel.open(file, StandardOpenOption.READ)) {
            plan = HprofRepairPlanner.plan(readChannel);
        }

        if (plan.isClean()) {
            LOG.info("No repairs needed: file={}", file);
            return plan.result();
        }

        try (FileChannel writeChannel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            apply(writeChannel, plan.repairs());
            writeChannel.force(true);
        }

        LOG.info("In-place HPROF sanitization complete: file={} repairs={} summary={}",
                file, plan.repairs().size(), plan.result().summaryMessage());
        return plan.result();
    }

    static void apply(FileChannel channel, List<HprofRepair> repairs) throws IOException {
        for (HprofRepair r : repairs) {
            switch (r) {
                case HprofRepair.PatchRecordLength p -> patchU4(channel, p.lengthFieldOffset(), (int) p.newLength());
                case HprofRepair.TruncateFile t -> {
                    if (channel.size() > t.offset()) {
                        channel.truncate(t.offset());
                    }
                }
                case HprofRepair.AppendEndMarker ignored -> appendEndMarker(channel);
            }
        }
    }

    private static void patchU4(FileChannel channel, long offset, int value) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(value);
        buf.flip();
        channel.position(offset);
        writeFully(channel, buf);
    }

    private static void appendEndMarker(FileChannel channel) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(RECORD_HEADER_SIZE);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) HprofRecordTag.HEAP_DUMP_END.value());
        buf.putInt(0);
        buf.putInt(0);
        buf.flip();
        channel.position(channel.size());
        writeFully(channel, buf);
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }
}
