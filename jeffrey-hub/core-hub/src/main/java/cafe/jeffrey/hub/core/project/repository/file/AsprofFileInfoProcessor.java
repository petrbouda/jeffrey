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
 * An async-profiler repository: chunks named {@code profile-<yyyyMMdd-HHmmss>.jfr}, so the newest
 * file is the last one by name.
 *
 * <p>Reading the instant back out of that name belonged here once, together with a fallback
 * processor for the files it did not fit. It is
 * {@link cafe.jeffrey.shared.common.model.repository.TimestampResolver} now, reached through the
 * file's own type, because which files carry their timestamp in their name is the same question
 * as which files may be compressed — and the two answers drifting apart is what let an archive
 * take the open chunk's place.
 */
public class AsprofFileInfoProcessor implements FileInfoProcessor {

    @Override
    public Comparator<Path> comparator() {
        return Comparator.comparing((Path f) -> f.getFileName().toString()).reversed();
    }
}
