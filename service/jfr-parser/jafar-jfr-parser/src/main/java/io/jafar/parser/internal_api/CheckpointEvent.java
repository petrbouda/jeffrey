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

package io.jafar.parser.internal_api;

import io.jafar.parser.*;
import io.jafar.parser.internal_api.ParserContext;
import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.parser.internal_api.metadata.MetadataClass;

import java.io.IOException;

public final class CheckpointEvent extends AbstractEvent {
    public final long startTime;
    public final long duration;
    public final int nextOffsetDelta;

    public final boolean isFlush;

    private final RecordingStream stream;

    CheckpointEvent(RecordingStream stream) throws IOException {
        super(stream);
        this.stream = stream;
        int size = (int) stream.readVarint();
        if (size == 0) {
            throw new IOException("Unexpected event size. Should be > 0");
        }
        long typeId = stream.readVarint();
        if (typeId != 1) {
            throw new IOException("Unexpected event type: " + typeId + " (should be 1)");
        }
        this.startTime = stream.readVarint();
        this.duration = stream.readVarint();
        this.nextOffsetDelta = (int)stream.readVarint();
        this.isFlush = stream.read() != 0;
    }

    void readConstantPools() throws IOException {
        ParserContext context = stream.getContext();
        TypeFilter typeFilter = context.getTypeFilter();

        boolean skipAll = context.getConstantPools().isReady();

        long cpCount = stream.readVarint();
        for (long i = 0; i < cpCount; i++) {
            long typeId = 0;
            while ((typeId = stream.readVarint()) == 0) ; // workaround for a bug in JMC JFR writer
            try {
                int count = (int) stream.readVarint();
                MetadataClass clz = context.getMetadataLookup().getClass(typeId);
                boolean skip = skipAll || (typeFilter != null && !typeFilter.test(clz));
                for (int j = 0; j < count; j++) {
                    long id = stream.readVarint();
                    if (skip) {
                        ValueLoader.skip(stream, clz, false, false);
                    } else {
                        MutableConstantPool constantPool = ((MutableConstantPools)context.getConstantPools()).addOrGetConstantPool(stream, typeId);
                        constantPool.addOffset(id, stream.position());
                        ValueLoader.skip(stream, clz, false, false);
                    }
                }
            } catch (IOException e) {
                throw e;
            }
        }
    }
}
