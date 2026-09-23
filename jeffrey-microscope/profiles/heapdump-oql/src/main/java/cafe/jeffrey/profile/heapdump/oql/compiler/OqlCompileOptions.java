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
package cafe.jeffrey.profile.heapdump.oql.compiler;

/**
 * Per-query knobs threaded into {@code OqlCompiler.compile}. Today there is
 * only one — whether to also scan {@code java.lang.String} instances whose
 * decoded content exceeded the indexer's content cap (the
 * {@code string_content.content IS NULL} rows). Off by default; the UI
 * "Scan large Strings" checkbox flips it on per query.
 */
public record OqlCompileOptions(boolean scanLargeStrings) {

    public static final OqlCompileOptions DEFAULTS = new OqlCompileOptions(false);
}
