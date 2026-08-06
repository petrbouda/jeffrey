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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.IntSupplier;

/**
 * How many event types the Advisor may analyze at once, read from the global settings each time a
 * batch launches so an edit on the settings page reaches the next run rather than the next restart.
 *
 * <p>The stored value is a plain count, except for {@code 0}, which the settings page offers as
 * <em>All</em> and which means no ceiling at all. Zero is the right shape for that: the page cannot
 * offer "every type" as a number, because a recording is only analyzed for the event types it
 * actually carries — one for a CPU-only recording, four for a full one — so a stored 4 would be a
 * ceiling on some recordings and no ceiling on others.</p>
 *
 * <p>A stored value that makes no sense falls back to the default rather than taking the Advisor
 * down: a value edited by hand, or written before a validation rule existed, must not stop runs.</p>
 */
public final class AdvisorParallelism implements IntSupplier {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorParallelism.class);

    /** The stored value meaning "no ceiling" — what the settings page shows as <em>All</em>. */
    public static final int UNLIMITED = 0;

    public static final int DEFAULT_MAX_CONCURRENT_RUNS = 2;

    private final SettingsStore settingsStore;

    public AdvisorParallelism(SettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    /**
     * @return the ceiling to apply, already translated from the stored value — never below 1, and
     *         {@link PipelineRunOptions#UNBOUNDED} when the setting says <em>All</em>
     */
    @Override
    public int getAsInt() {
        int stored = settingsStore.getInt(
                MicroscopeSettingKeys.ADVISOR_MAX_CONCURRENT_RUNS, DEFAULT_MAX_CONCURRENT_RUNS);

        if (stored == UNLIMITED) {
            return PipelineRunOptions.UNBOUNDED;
        }
        if (stored < 1) {
            LOG.warn("Ignoring out-of-range advisor concurrency ceiling: max_concurrent_runs={}", stored);
            return DEFAULT_MAX_CONCURRENT_RUNS;
        }
        return stored;
    }
}
