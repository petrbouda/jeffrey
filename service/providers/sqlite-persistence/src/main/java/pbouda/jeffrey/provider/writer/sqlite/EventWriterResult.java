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

package pbouda.jeffrey.provider.writer.sqlite;

import pbouda.jeffrey.common.model.ActiveSetting;
import pbouda.jeffrey.provider.api.model.EventTypeBuilder;
import pbouda.jeffrey.provider.writer.sqlite.model.EventThreadWithId;

import java.util.List;
import java.util.Map;

public record EventWriterResult(
        List<EventThreadWithId> eventThreads,
        List<EventTypeBuilder> eventTypes,
        Map<String, ActiveSetting> activeSettings) {
}
