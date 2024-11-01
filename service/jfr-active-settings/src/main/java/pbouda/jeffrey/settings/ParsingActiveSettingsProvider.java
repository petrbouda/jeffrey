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

package pbouda.jeffrey.settings;

import pbouda.jeffrey.jfrparser.jdk.JdkRecordingIterators;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ParsingActiveSettingsProvider implements ActiveSettingsProvider {

    private final List<Path> recordings;

    public ParsingActiveSettingsProvider(List<Path> recordings) {
        this.recordings = recordings;
    }

    @Override
    public ActiveSettings get() {
        Map<String, ActiveSetting> activeSettingsMap = JdkRecordingIterators.automaticAndCollectPartial(
                recordings, ActiveSettingsProcessor::new, new ActiveSettingsCollector());

        return new ActiveSettings(activeSettingsMap);
    }
}
