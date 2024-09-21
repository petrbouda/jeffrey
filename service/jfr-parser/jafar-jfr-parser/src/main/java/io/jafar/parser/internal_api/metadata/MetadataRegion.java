

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
