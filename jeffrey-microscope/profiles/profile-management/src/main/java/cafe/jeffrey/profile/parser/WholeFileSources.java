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

package cafe.jeffrey.profile.parser;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Parses each source file as it lies.
 * <p>
 * Nothing is read, written or created: the file the recording arrived as is the file handed to the
 * parser. That is the whole point of this mode — with enough files there is no parallelism left to
 * buy, so the split would be a full copy of the recording paid for nothing.
 */
record WholeFileSources() implements SourceParseMode {

    @Override
    public void expand(Path source, Path scratchDir, Consumer<Path> onUnit) {
        onUnit.accept(source);
    }
}
