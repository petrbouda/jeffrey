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

import cafe.jeffrey.shared.common.compression.Lz4Compressor;

import java.nio.file.Path;

/**
 * LZ4, appended to the name the file already has, so {@code profile-1.jfr} becomes
 * {@code profile-1.jfr.lz4} — a name {@link SupportedRecordingFile} classifies as JFR_LZ4, which
 * strips to the same id and carries the same timestamp.
 */
final class Lz4Compression implements Compression {

    private static final String SUFFIX = "." + FileExtensions.LZ4;

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public Path target(Path source) {
        return source.resolveSibling(source.getFileName() + SUFFIX);
    }

    @Override
    public Path compress(Path source, Path target) {
        return Lz4Compressor.compress(source, target);
    }
}
