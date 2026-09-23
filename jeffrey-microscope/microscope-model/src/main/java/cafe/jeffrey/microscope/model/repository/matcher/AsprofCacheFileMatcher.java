/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.microscope.model.repository.matcher;

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
