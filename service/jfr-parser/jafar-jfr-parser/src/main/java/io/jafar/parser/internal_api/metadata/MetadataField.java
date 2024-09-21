

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
