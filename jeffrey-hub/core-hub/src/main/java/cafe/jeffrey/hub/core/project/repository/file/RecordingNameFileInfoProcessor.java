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

package cafe.jeffrey.hub.core.project.repository.file;

import java.nio.file.Path;
import java.util.Comparator;

/**
 * For a layout whose filenames carry their own order, newest first by name.
 *
 * <p>Named for async-profiler once, whose chunks are {@code profile-<yyyyMMdd-HHmmss>.jfr} and so
 * sort by name — but the rule is about names sorting, not about who wrote them, which is why it no
 * longer carries that profiler's name. It is the only implementation there is: a sibling ordering
 * by modification time was written for a layout whose names say nothing about order, and nothing
 * ever wired it, so it went rather than sit there implying such a layout exists.
 *
 * <p>Reading the instant back out of the name belonged here too, once. It is
 * {@code TimestampResolver} now, reached through the file's own type, because which files carry a
 * timestamp in their name is the same question as which files may be compressed — and the two
 * answers drifting apart is what let an archive take the open chunk's place.
 */
public class RecordingNameFileInfoProcessor implements FileInfoProcessor {

    @Override
    public Comparator<Path> comparator() {
        return Comparator.comparing((Path f) -> f.getFileName().toString()).reversed();
    }
}
