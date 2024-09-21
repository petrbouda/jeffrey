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

import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MetadataField extends AbstractMetadataElement {
    private final List<MetadataAnnotation> annotations = new ArrayList<>();
    private final long classId;
    private final boolean hasConstantPool;
    private final int dimension;

    MetadataField(RecordingStream stream, ElementReader reader, boolean forceConstantPools) throws IOException {
        super(stream, MetadataElementKind.FIELD);
        classId = Long.parseLong(getAttribute("class"));
        hasConstantPool = forceConstantPools || Boolean.parseBoolean(getAttribute("constantPool"));
        dimension = Integer.parseInt(getAttribute("dimension", "-1"));
        resetAttributes();
        readSubelements(reader);
    }

    public MetadataClass getType() {
        return metadataLookup.getClass(classId);
    }

    public long getTypeId() {
        return classId;
    }

    public boolean hasConstantPool() {
        return hasConstantPool;
    }

    public int getDimension() {
        return dimension;
    }

    @Override
    protected void onSubelement(AbstractMetadataElement element) {
        if (element.getKind() == MetadataElementKind.ANNOTATION) {
            annotations.add((MetadataAnnotation) element);
        } else {
            throw new IllegalStateException("Unexpected subelement: " + element.getKind());
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visitField(this);
        annotations.forEach(a -> a.accept(visitor));
        visitor.visitEnd(this);
    }

    @Override
    public String toString() {
        return "MetadataField{" +
                "type='" + (getType() != null ? getType().getName() : classId) + '\'' +
                ", name='" + getName() + "'" +
                ", hasConstantPool=" + hasConstantPool +
                ", dimension=" + dimension +
                '}';
    }
}
