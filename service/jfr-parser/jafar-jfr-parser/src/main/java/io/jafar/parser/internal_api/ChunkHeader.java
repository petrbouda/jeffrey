

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
