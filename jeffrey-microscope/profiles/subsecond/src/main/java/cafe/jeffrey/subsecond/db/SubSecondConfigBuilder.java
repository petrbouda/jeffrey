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

package cafe.jeffrey.subsecond.db;

import cafe.jeffrey.microscope.model.ProfileInfo;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.microscope.model.time.RelativeTimeRange;

import java.util.Objects;

public final class SubSecondConfigBuilder {

    private static final int DEFAULT_BUCKET_SIZE_MS = 20;

    private ProfileInfo profileInfo;
    private Type eventType;
    private RelativeTimeRange timeRange;
    private boolean collectWeight;
    private int bucketSizeMs = DEFAULT_BUCKET_SIZE_MS;

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

    public SubSecondConfigBuilder withBucketSizeMs(int bucketSizeMs) {
        this.bucketSizeMs = bucketSizeMs;
        return this;
    }

    public SubSecondConfig build() {
        Objects.requireNonNull(eventType, "Type of the event needs to be specified");

        return new SubSecondConfig(
                profileInfo,
                eventType,
                timeRange,
                collectWeight,
                bucketSizeMs);
    }
}
