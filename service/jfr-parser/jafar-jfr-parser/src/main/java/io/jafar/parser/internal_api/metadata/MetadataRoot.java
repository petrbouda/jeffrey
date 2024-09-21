

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
