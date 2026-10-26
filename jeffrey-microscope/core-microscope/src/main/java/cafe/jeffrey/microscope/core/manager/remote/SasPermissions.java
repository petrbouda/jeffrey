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

package cafe.jeffrey.microscope.core.manager.remote;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The rights a SAS token grants on a share, read from its {@code sp} field — the only part of the
 * token this ever keeps, so it may be logged.
 *
 * @param letters the granted rights, one letter each ({@code r} read, {@code l} list, {@code c}
 *                create, {@code w} write, {@code d} delete); empty when the token names none
 */
record SasPermissions(Set<Character> letters) {

    private static final String PERMISSIONS_FIELD = "sp";
    private static final String FIELD_SEPARATOR = "&";
    private static final String VALUE_SEPARATOR = "=";
    private static final String QUERY_START = "?";
    private static final Set<Character> CHANGES = Set.of('c', 'w', 'd');
    private static final Set<Character> EVERY_CHANGE = Set.of('r', 'l', 'c', 'w', 'd');

    SasPermissions {
        letters = Set.copyOf(letters);
    }

    static SasPermissions of(String token) {
        String query = token.startsWith(QUERY_START) ? token.substring(1) : token;
        return Arrays.stream(query.split(FIELD_SEPARATOR))
                .map(field -> field.split(VALUE_SEPARATOR, 2))
                .filter(pair -> pair.length == 2 && pair[0].equals(PERMISSIONS_FIELD))
                .findFirst()
                .map(pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8))
                .map(value -> new SasPermissions(value.chars().mapToObj(letter -> (char) letter).collect(Collectors.toSet())))
                .orElseGet(() -> new SasPermissions(Set.of()));
    }

    /** Whether the token can change the share at all. */
    boolean grantsChanges() {
        return letters.stream().anyMatch(CHANGES::contains);
    }

    /** Whether the token can make every change a writable source makes. */
    boolean grantsEveryChange() {
        return letters.containsAll(EVERY_CHANGE);
    }

    @Override
    public String toString() {
        return letters.stream().sorted().map(String::valueOf).collect(Collectors.joining());
    }
}
