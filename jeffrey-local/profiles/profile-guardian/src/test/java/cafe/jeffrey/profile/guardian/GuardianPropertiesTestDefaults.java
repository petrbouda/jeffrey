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

package cafe.jeffrey.profile.guardian;

/**
 * Test-only factory for {@link GuardianProperties} that mirrors the {@code @DefaultValue}
 * annotations in the production record. Used by tests and non-Spring callers that need a
 * fully-populated properties instance without wiring up the Spring binder.
 * <p>
 * When a new field is added to {@link GuardianProperties}, update {@link #defaults()} to
 * match. The parity test {@code GuardianPropertiesDefaultsParityTest} reflects on the record
 * canonical constructor and asserts every {@code @DefaultValue} matches the value this
 * factory returns.
 */
public final class GuardianPropertiesTestDefaults {

    private GuardianPropertiesTestDefaults() {
    }

    public static GuardianProperties defaults() {
        return new GuardianProperties(
                1000,
                0.03, 0.02, 0.03, 0.02,
                0.04, 0.02, 0.04, 0.02,
                0.05, 0.03, 0.05, 0.03,
                0.05, 0.03, 0.05, 0.03,
                0.05, 0.03, 0.05, 0.03,
                0.05, 0.03, 0.05, 0.03,
                0.05, 0.03, 0.05, 0.03,
                0.2, 0.15, 0.05, 0.03,
                0.05, 0.03, 0.05, 0.03,
                0.1, 0.07,
                0.03, 0.01,
                1000,
                0.1, 0.07, 0.05, 0.03,
                0.05, 0.03, 0.05, 0.03,
                0.05, 0.03, 0.05, 0.03,
                0.05, 0.03, 0.05, 0.03,
                0.15, 0.1,
                1000,
                100,
                0.05, 0.03, 0.05, 0.03,
                0.05, 0.03, 0.05, 0.03,
                0.05, 0.03, 0.05, 0.03,
                100, 50,
                20, 10
        );
    }
}
