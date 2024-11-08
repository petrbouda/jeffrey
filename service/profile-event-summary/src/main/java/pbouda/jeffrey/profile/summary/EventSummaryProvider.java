/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.profile.summary;

import pbouda.jeffrey.common.filesystem.ProfileDirs;
import pbouda.jeffrey.profile.settings.ActiveSettingsProvider;
import pbouda.jeffrey.profile.summary.event.EventSummary;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public interface EventSummaryProvider extends Supplier<List<EventSummary>> {

    @FunctionalInterface
    interface Factory extends Function<ProfileDirs, EventSummaryProvider> {
    }
}
