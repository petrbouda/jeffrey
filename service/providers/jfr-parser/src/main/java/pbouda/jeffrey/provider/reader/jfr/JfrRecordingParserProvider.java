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

package pbouda.jeffrey.provider.reader.jfr;

import pbouda.jeffrey.common.compression.Lz4Compressor;
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.provider.api.RecordingEventParser;
import pbouda.jeffrey.provider.api.RecordingInformationParser;
import pbouda.jeffrey.provider.api.RecordingParserProvider;

public class JfrRecordingParserProvider implements RecordingParserProvider {

    private Lz4Compressor lz4Compressor;
    private JeffreyDirs jeffreyDirs;

    @Override
    public void initialize(JeffreyDirs jeffreyDirs) {
        this.lz4Compressor = new Lz4Compressor(jeffreyDirs);
        this.jeffreyDirs = jeffreyDirs;
    }

    @Override
    public RecordingEventParser newRecordingEventParser() {
        return new JfrRecordingEventParser(jeffreyDirs, lz4Compressor);
    }

    @Override
    public RecordingInformationParser newRecordingInformationParser() {
        return new JfrRecordingInformationParser(jeffreyDirs, lz4Compressor);
    }
}
