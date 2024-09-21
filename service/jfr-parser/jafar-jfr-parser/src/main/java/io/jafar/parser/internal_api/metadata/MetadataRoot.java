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

public final class MetadataRoot extends AbstractMetadataElement {
    private MetadataElement metadata;
    private MetadataRegion region;

    MetadataRoot(RecordingStream stream, ElementReader reader) throws IOException {
        super(stream, MetadataElementKind.ROOT);
        resetAttributes();
        readSubelements(reader);
    }

    @Override
    protected void onSubelement(AbstractMetadataElement element) {
        if (element.getKind() == MetadataElementKind.META) {
            metadata = (MetadataElement) element;
        } else if (element.getKind() == MetadataElementKind.REGION) {
            region = (MetadataRegion) element;
        } else {
            throw new IllegalStateException("Unexpected subelement: " + element.getKind());
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visitRoot(this);
        metadata.accept(visitor);
        region.accept(visitor);
        visitor.visitEnd(this);
    }

    @Override
    public String toString() {
        return "MetadataRoot";
    }
}
