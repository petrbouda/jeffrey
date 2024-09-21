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

package io.jafar.parser;

import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ParsingUtils {
    private static final byte[] COMMON_BUFFER = new byte[4096]; // reusable byte buffer

    public static String bytesToString(byte[] array, int offset, int len) {
      StringBuilder sb = new StringBuilder("[");
      boolean comma = false;
      for (int i = 0; i < len; i++) {
        if (comma) {
          sb.append(", ");
        } else {
          comma = true;
        }
        sb.append(array[i + offset]);
      }
      sb.append(']');
      return sb.toString();
    }

    public static String readUTF8(RecordingStream stream) throws IOException {
      byte id = stream.read();
      if (id == 0) {
        return null;
      } else if (id == 1) {
        return "";
      } else if (id == 2) {
        // string constant
        int ptr = (int)stream.readVarint();
        return stream.getContext().getMetadataLookup().getString(ptr);
      } else if (id == 3) {
        // UTF8
        int size = (int) stream.readVarint();
        byte[] content = size <= COMMON_BUFFER.length ? COMMON_BUFFER : new byte[size];
        stream.read(content, 0, size);
        return new String(content, 0, size, StandardCharsets.UTF_8);
      } else if (id == 4) {
        int size = (int) stream.readVarint();
        char[] chars = new char[size];
        for (int i = 0; i < size; i++) {
          chars[i] = (char) stream.readVarint();
        }
        return new String(chars);
      } else if (id == 5) {
        // LATIN1
        int size = (int) stream.readVarint();
        byte[] content = size <= COMMON_BUFFER.length ? COMMON_BUFFER : new byte[size];
        stream.read(content, 0, size);
        return new String(content, 0, size, StandardCharsets.ISO_8859_1);
      } else {
        throw new IOException("Unexpected string constant id: " + id);
      }
    }

  public static void skipUTF8(RecordingStream stream) throws IOException {
    byte id = stream.read();
    switch (id) {
      case 3, 5 -> {
        int size = (int) stream.readVarint();
        stream.skip(size);
      }
      case 4 -> {
        int size = (int) stream.readVarint();
        for (int i = 0; i < size; i++) {
          stream.readVarint();
        }
      }
      case 2 -> {
        stream.readVarint();
      }
    }
  }
}
