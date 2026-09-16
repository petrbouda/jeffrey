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

package cafe.jeffrey.hub.core.project.repository;

/**
 * The file an id named was in the session's listing and gone by the time it was reached.
 *
 * <p>On this hub that means one thing: the compression job published the archive and removed the
 * recording in between. The id is the one thing that survives that rewrite, so asking for it
 * again names the archive — which is why this is a kind of its own rather than one more refusal
 * worded differently. A caller holding an id has somewhere to go; a caller holding a path does
 * not.
 *
 * <p>An {@link IllegalArgumentException}, so a caller that knows nothing about this still reports
 * it the way it reports every other refusal from {@link RepositoryStorage#file}: the id named
 * nothing that can be served.
 */
public class FileVanishedException extends IllegalArgumentException {

    public FileVanishedException(String message) {
        super(message);
    }
}
