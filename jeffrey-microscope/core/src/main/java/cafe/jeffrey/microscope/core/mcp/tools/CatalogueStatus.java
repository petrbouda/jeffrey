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

package cafe.jeffrey.microscope.core.mcp.tools;

/**
 * Whether a catalogue listing has anything in it: the same two values for {@code profiles_list} and
 * {@code recordings_list}, so an empty catalogue reads the same in both.
 */
public enum CatalogueStatus {

    /** The listing matched at least one entry; a page may still be empty past the end of a cursor. */
    OK,

    /** Nothing to list: the catalogue is empty, or nothing matched the search. {@code reason} says which. */
    EMPTY
}
