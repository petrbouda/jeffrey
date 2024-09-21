

package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.parser.internal_api.metadata.AbstractMetadataElement;
import io.jafar.parser.internal_api.metadata.MetadataClass;
import io.jafar.parser.internal_api.metadata.MetadataElementKind;
import io.jafar.parser.internal_api.metadata.MetadataVisitor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MetadataElement extends AbstractMetadataElement {
    private final List<io.jafar.parser.internal_api.metadata.MetadataClass> classes = new ArrayList<>();

    public MetadataElement(RecordingStream stream, io.jafar.parser.internal_api.metadata.ElementReader reader) throws IOException {
        super(stream, MetadataElementKind.META);
        resetAttributes();
        readSubelements(reader);
    }

    @Override
    protected void onSubelement(AbstractMetadataElement element) {
        if (element.getKind() == MetadataElementKind.CLASS) {
            io.jafar.parser.internal_api.metadata.MetadataClass clz = (MetadataClass) element;
            classes.add(clz);
        } else {
            throw new IllegalStateException("Unexpected subelement: " + element.getKind());
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visitMetadata(this);
        classes.forEach(c -> c.accept(visitor));
    }

    @Override
    public String toString() {
        return "MetadataElement";
    }
}
