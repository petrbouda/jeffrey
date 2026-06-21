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

package cafe.jeffrey.performance.analyst.persistence;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Supported version-control platforms for a project's {@link VersionSystem} integration. The
 * {@link #code()} is the persisted/transport form ({@code github}, {@code gitlab}).
 */
public enum Platform {

    GITHUB("github"),
    GITLAB("gitlab");

    private static final Map<String, Platform> BY_CODE =
            Arrays.stream(values()).collect(Collectors.toMap(Platform::code, Function.identity()));

    private final String code;

    Platform(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Platform fromCode(String code) {
        Platform platform = BY_CODE.get(code);
        if (platform == null) {
            throw new IllegalArgumentException("Unknown version-system platform: " + code);
        }
        return platform;
    }
}
