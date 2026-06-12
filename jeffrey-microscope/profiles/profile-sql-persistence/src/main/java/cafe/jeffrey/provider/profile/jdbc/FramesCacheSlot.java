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
