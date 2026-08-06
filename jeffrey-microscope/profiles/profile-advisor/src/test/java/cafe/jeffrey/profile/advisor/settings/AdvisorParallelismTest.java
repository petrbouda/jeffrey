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

package cafe.jeffrey.profile.advisor.settings;

import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.shared.common.config.MicroscopeSettingKeys;
import cafe.jeffrey.shared.common.config.SettingsStore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdvisorParallelismTest {

    /**
     * The key has to be declared for a stored value to be readable at all — an undeclared name is
     * dropped as unknown, which is how the real store keeps a stale row from resurrecting a setting.
     */
    private static AdvisorParallelism withStored(String value) {
        SettingsStore store = new SettingsStore(
                Map.of(MicroscopeSettingKeys.ADVISOR_MAX_CONCURRENT_RUNS, "2"),
                Map.of(MicroscopeSettingKeys.ADVISOR_MAX_CONCURRENT_RUNS, value));
        return new AdvisorParallelism(store);
    }

    @Nested
    class StoredCeiling {

        @Test
        void isUsedAsWritten() {
            assertEquals(3, withStored("3").getAsInt());
        }

        @Test
        void ofOneIsAllowed() {
            assertEquals(1, withStored("1").getAsInt());
        }
    }

    @Nested
    class Unlimited {

        // The settings page cannot offer "every type" as a number — a recording is analyzed only for the
        // types it carries — so zero is what All stores.
        @Test
        void isWhatZeroMeans() {
            assertEquals(PipelineRunOptions.UNBOUNDED, withStored("0").getAsInt());
        }
    }

    @Nested
    class UnusableValue {

        @Test
        void fallsBackToTheDefaultRatherThanFailingTheRun() {
            assertEquals(AdvisorParallelism.DEFAULT_MAX_CONCURRENT_RUNS, withStored("-1").getAsInt());
            assertEquals(AdvisorParallelism.DEFAULT_MAX_CONCURRENT_RUNS, withStored("not-a-number").getAsInt());
        }

        @Test
        void undeclaredFallsBackToTheDefault() {
            SettingsStore empty = new SettingsStore(Map.of(), Map.of());
            assertEquals(
                    AdvisorParallelism.DEFAULT_MAX_CONCURRENT_RUNS,
                    new AdvisorParallelism(empty).getAsInt());
        }
    }
}
