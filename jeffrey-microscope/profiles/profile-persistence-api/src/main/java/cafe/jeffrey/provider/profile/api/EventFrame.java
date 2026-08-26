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

package cafe.jeffrey.provider.profile.api;

/**
 * One stack frame on its way into the profile database.
 *
 * @param clazz          class name with any hidden-class address stripped off, so it stays stable
 *                       across runs
 * @param method         method name
 * @param type           raw frame type code (e.g. "Interpreted", "JIT compiled", "Inlined")
 * @param bci            bytecode index
 * @param line           line number
 * @param hiddenClassId  per-run identity of a hidden class (e.g. {@code 0x0000000011cb1be8}),
 *                       {@code null} for ordinary classes
 */
public record EventFrame(String clazz, String method, String type, long bci, long line, String hiddenClassId) {

    /**
     * A frame on an ordinary (non-hidden) class.
     */
    public EventFrame(String clazz, String method, String type, long bci, long line) {
        this(clazz, method, type, bci, line, null);
    }
}
