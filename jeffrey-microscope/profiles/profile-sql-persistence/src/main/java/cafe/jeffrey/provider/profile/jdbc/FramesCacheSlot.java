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

package cafe.jeffrey.provider.profile.jdbc;

import javax.sql.DataSource;
import java.util.function.Supplier;

/**
 * Per-profile view of the {@link SingleSlotFramesCache}: binds the shared single-slot cache to the
 * profile database the owning repository was created for, so repositories don't have to carry the
 * cache and the data source separately.
 */
public record FramesCacheSlot(SingleSlotFramesCache cache, DataSource dataSource) {

    public FramesCache resolve(Supplier<FramesCache> loader) {
        return cache.resolve(dataSource, loader);
    }

    public void invalidate() {
        cache.invalidate(dataSource);
    }
}
