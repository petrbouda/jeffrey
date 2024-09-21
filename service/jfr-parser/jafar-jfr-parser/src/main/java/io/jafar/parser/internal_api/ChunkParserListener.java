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

import io.jafar.parser.internal_api.metadata.MetadataEvent;

/**
 * A callback to be provided to {@linkplain StreamingChunkParser#parse(java.nio.ByteBuffer, ChunkParserListener)}
 */
public interface ChunkParserListener {
  /** Called when the recording starts to be processed */
  default void onRecordingStart(ParserContext context) {}

  /**
   * Called for each discovered chunk
   *
   * @param chunkIndex the chunk index (1-based)
   * @param header the parsed chunk header
   * @return {@literal false} if the chunk should be skipped
   */
  default boolean onChunkStart(int chunkIndex, ChunkHeader header, ParserContext context) {
    return true;
  }

  /**
   * Called for the chunk metadata event
   *
   * @param metadata the chunk metadata event
   * @return {@literal false} if the remainder of the chunk should be skipped
   */
  default boolean onMetadata(MetadataEvent metadata) {
    return true;
  }

  default boolean onCheckpoint(CheckpointEvent checkpoint) { return true; }

  /**
   * Called for each parsed event
   *
   * @param typeId event type id
   * @param stream {@linkplain RecordingStream} positioned at the event payload start
   * @param payloadSize the size of the payload in bytes
   * @return {@literal false} if the remainder of the chunk should be skipped
   */
  default boolean onEvent(long typeId, RecordingStream stream, long payloadSize) {
    return true;
  }

  /**
   * Called when a chunk is fully processed or skipped
   *
   * @param chunkIndex the chunk index (1-based)
   * @param skipped {@literal true} if the chunk was skipped
   * @return {@literal false} if the remaining chunks in the recording should be skipped
   */
  default boolean onChunkEnd(int chunkIndex, boolean skipped) {
    return true;
  }

  /** Called when the recording was fully processed */
  default void onRecordingEnd() {}
}
