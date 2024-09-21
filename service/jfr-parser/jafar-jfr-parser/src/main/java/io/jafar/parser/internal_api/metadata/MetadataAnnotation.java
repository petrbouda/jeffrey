

package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class MetadataAnnotation extends AbstractMetadataElement {
    private final List<MetadataAnnotation> annotations = new ArrayList<>();

    public final long classId;
    public final String value;
    MetadataAnnotation(RecordingStream stream, io.jafar.parser.internal_api.metadata.ElementReader reader) throws IOException {
        super(stream, MetadataElementKind.ANNOTATION);
        this.classId = Long.parseLong(getAttribute("class"));
        this.value = getAttribute("value");
        resetAttributes();
        readSubelements(reader);
    }

    public MetadataClass getType() {
        return metadataLookup.getClass(classId);
    }

    public long getClassId() {
        return classId;
    }

    public String getValue() {
        return value;
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
        visitor.visitAnnotation(this);
        annotations.forEach(a -> a.accept(visitor));
        visitor.visitEnd(this);
    }

    @Override
    public String toString() {
        return "MetadataAnnotation{" +
                "type='" + (getType() != null ? getType().getName() : classId) + '\'' +
                ", value='" + value + '\'' +
                '}';
    }
}
