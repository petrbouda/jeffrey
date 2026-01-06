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

package pbouda.jeffrey.jmh.flamegraph.verification;

/**
 * Verification mode for benchmark output comparison.
 */
public enum VerificationMode {
    /**
     * Default: Compare only SHA-256 hashes of the output.
     * Fast and sufficient for most cases.
     */
    HASH_ONLY,

    /**
     * Compare full JSON output and save to file for debugging.
     * Use when you need to see the actual differences.
     */
    FULL_OUTPUT
}
