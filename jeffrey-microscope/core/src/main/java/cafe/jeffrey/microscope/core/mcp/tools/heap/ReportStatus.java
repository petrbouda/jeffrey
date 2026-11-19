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


package cafe.jeffrey.microscope.core.mcp.tools.heap;

/**
 * Whether a cached heap report could be read. A report is computed once, by {@code heap_prepare} or the
 * report's page in the UI, and read many times; before that it has nothing to say.
 */
public enum ReportStatus {

    /** The report was computed and is the answer. */
    OK,
    /** The report was never computed for this dump; {@code heap_prepare} computes it. */
    NOT_RUN_YET
}
