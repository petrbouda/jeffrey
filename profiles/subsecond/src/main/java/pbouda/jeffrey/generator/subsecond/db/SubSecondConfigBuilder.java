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

package pbouda.jeffrey.generator.subsecond.db;

import pbouda.jeffrey.shared.model.ProfileInfo;
import pbouda.jeffrey.shared.model.Type;
import pbouda.jeffrey.shared.model.time.RelativeTimeRange;

import java.util.Objects;

public final class SubSecondConfigBuilder {
    private ProfileInfo profileInfo;
    private Type eventType;
    private RelativeTimeRange timeRange;
    private boolean collectWeight;

    public SubSecondConfigBuilder withProfileInfo(ProfileInfo profileInfo) {
        this.profileInfo = profileInfo;
        return this;
    }

    public SubSecondConfigBuilder withEventType(Type eventType) {
        this.eventType = eventType;
        return this;
    }

    public SubSecondConfigBuilder withTimeRange(RelativeTimeRange timeRange) {
        this.timeRange = timeRange;
        return this;
    }

    public SubSecondConfigBuilder withCollectWeight(boolean collectWeight) {
        this.collectWeight = collectWeight;
        return this;
    }

    public SubSecondConfig build() {
        Objects.requireNonNull(eventType, "Type of the event needs to be specified");

        return new SubSecondConfig(
                profileInfo,
                eventType,
                timeRange,
                collectWeight);
    }
}
