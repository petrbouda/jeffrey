/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.common.model.repository.matcher;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class AsprofCacheFileMatcher implements Predicate<String> {

    private static final Pattern ASPROF_CACHE_PATTERN = Pattern.compile(".*\\.jfr\\.[0-9]+~$");

    @Override
    public boolean test(String filename) {
        if (filename == null) {
            return false;
        }
        return ASPROF_CACHE_PATTERN.matcher(filename).matches();
    }
}
