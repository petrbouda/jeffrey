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

package cafe.jeffrey.jfrparser.raw;

/**
 * Constants for JFR chunk parsing.
 */
public interface JfrChunkConstants {

    /**
     * Size of the JFR chunk header in bytes.
     */
    int CHUNK_HEADER_SIZE = 68;

    /**
     * Magic number identifying a JFR chunk ("FLR\0").
     */
    int CHUNK_MAGIC = 0x464c5200;

    /**
     * Feature flag mask indicating this is the final chunk in a recording.
     */
    int MASK_FINAL_CHUNK = 1 << 1;
}
