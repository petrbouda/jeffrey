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
