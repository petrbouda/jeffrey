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

package cafe.jeffrey.shared.common.model.repository;

import java.nio.file.Path;

/**
 * The answer for a type that must be left as it is — because no compressed form of it would still
 * be recognised as the same file, not because nobody has written one yet.
 */
final class NoCompression implements Compression {

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public Path target(Path source) {
        throw new UnsupportedOperationException("This file type has no compressed form: " + source);
    }

    @Override
    public Path compress(Path source, Path target) {
        throw new UnsupportedOperationException("This file type has no compressed form: " + source);
    }
}
