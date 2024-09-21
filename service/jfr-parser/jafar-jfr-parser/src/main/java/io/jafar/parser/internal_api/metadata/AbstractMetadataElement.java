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

package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.MutableMetadataLookup;
import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMetadataElement {

    private final RecordingStream stream;

    final MutableMetadataLookup metadataLookup;

    private final Map<String, String> attributes;

    protected final String name;
    private final io.jafar.parser.internal_api.metadata.MetadataElementKind kind;

    AbstractMetadataElement(RecordingStream stream, io.jafar.parser.internal_api.metadata.MetadataElementKind kind) throws IOException {
        this.stream = stream;
        this.kind = kind;
        this.metadataLookup = (MutableMetadataLookup) stream.getContext().getMetadataLookup();
        this.attributes = processAttributes();
        this.name = getAttribute("name");
    }
    
    protected final String getAttribute(String key) {
        return attributes.get(key);
    }

    protected final String getAttribute(String key, String dflt) {
        return attributes.getOrDefault(key, dflt);
    }
    
    protected void resetAttributes() {
        attributes.clear();
    }

    protected final void readSubelements(io.jafar.parser.internal_api.metadata.ElementReader reader) throws IOException {
        // now inspect all the enclosed elements
        int elemCount = (int) stream.readVarint();
        for (int i = 0; i < elemCount; i++) {
            onSubelement(reader.readElement(stream));
        }
    }

    protected void onSubelement(AbstractMetadataElement element) {}

    abstract public void accept(MetadataVisitor visitor);

    private Map<String, String> processAttributes() throws IOException {
        Map<String, String> attributes = new HashMap<>();
        int attrCount = (int) stream.readVarint();
        for (int i = 0; i < attrCount; i++) {
            int keyPtr = (int) stream.readVarint();
            int valPtr = (int) stream.readVarint();
            attributes.put(metadataLookup.getString(keyPtr), metadataLookup.getString(valPtr));
        }
        return attributes;
    }

    public String getName() {
        return name;
    }

    public MetadataElementKind getKind() {
        return kind;
    }
}
