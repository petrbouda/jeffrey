/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package io.jafar.parser.internal_api;

import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.utils.BytePacking;

import java.io.IOException;
import java.nio.ByteOrder;

/** A chunk header data object */
public final class ChunkHeader {
  public static final int MAGIC_BE = BytePacking.pack(ByteOrder.BIG_ENDIAN, 'F', 'L', 'R', '\0');

  public final int order;
  public final int offset;
  public final short major;
  public final short minor;
  public final int size;
  public final int cpOffset;
  public final int metaOffset;
  public final long startNanos;
  public final long duration;
  public final long startTicks;
  public final long frequency;
  public final boolean compressed;

  ChunkHeader(RecordingStream recording, int index) throws IOException {
    order = index;
    offset = recording.position();
    int magic = recording.readInt();
    if (magic != MAGIC_BE) {
      throw new IOException("Invalid JFR Magic Number: " + Integer.toHexString(magic));
    }
    major = recording.readShort();
    minor = recording.readShort();
    size = (int) recording.readLong();
    cpOffset = (int) recording.readLong();
    metaOffset = (int) recording.readLong();
    startNanos = recording.readLong();
    duration = recording.readLong();
    startTicks = recording.readLong();
    frequency = recording.readLong();
    compressed = recording.readInt() != 0;
  }

  @Override
  public String toString() {
    return "ChunkHeader{"
        + "major="
        + major
        + ", minor="
        + minor
        + ", size="
        + size
        + ", offset="
        + offset
        + ", cpOffset="
        + cpOffset
        + ", metaOffset="
        + metaOffset
        + ", startNanos="
        + startNanos
        + ", duration="
        + duration
        + ", startTicks="
        + startTicks
        + ", frequency="
        + frequency
        + ", compressed="
        + compressed
        + '}';
  }

}
