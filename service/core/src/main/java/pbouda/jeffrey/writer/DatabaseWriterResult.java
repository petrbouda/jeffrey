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

package pbouda.jeffrey.writer;

import jdk.jfr.EventType;
import org.eclipse.collections.api.map.primitive.ObjectLongMap;
import pbouda.jeffrey.common.Type;
import pbouda.jeffrey.common.model.profile.EventThread;
import pbouda.jeffrey.profile.settings.ActiveSetting;
import pbouda.jeffrey.profile.settings.SettingNameLabel;

import java.util.List;
import java.util.Map;

public record DatabaseWriterResult(
        List<EventThread> eventThreads,
        List<EventType> eventTypes,
        ObjectLongMap<Type> samples,
        ObjectLongMap<Type> weight,
        Map<SettingNameLabel, ActiveSetting> activeSettings) {
}
