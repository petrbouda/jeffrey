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
import io.jafar.parser.internal_api.metadata.AbstractMetadataElement;
import io.jafar.parser.internal_api.metadata.MetadataElementKind;
import io.jafar.parser.internal_api.metadata.MetadataVisitor;

import java.io.IOException;

public final class MetadataRegion extends AbstractMetadataElement {
    private final long dst;
    private final long gmtOffset;
    private final String locale;

    MetadataRegion(RecordingStream stream, io.jafar.parser.internal_api.metadata.ElementReader reader) throws IOException {
        super(stream, MetadataElementKind.REGION);
        this.dst = Long.parseLong(getAttribute("dst", "0"));
        this.gmtOffset = Long.parseLong(getAttribute("gmtOffset", "0"));
        this.locale = getAttribute("locale", "en_US");
        resetAttributes();
        readSubelements(reader);
    }

    public long getDst() {
        return dst;
    }

    public long getGmtOffset() {
        return gmtOffset;
    }

    public String getLocale() {
        return locale;
    }

    @Override
    protected void onSubelement(AbstractMetadataElement element) {
        throw new IllegalStateException("Unexpected subelement: " + element.getKind());
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visitRegion(this);
        visitor.visitEnd(this);
    }

    @Override
    public String toString() {
        return "MetadataRegion{" +
                "dst=" + dst +
                ", gmtOffset=" + gmtOffset +
                ", locale='" + locale + '\'' +
                '}';
    }
}
