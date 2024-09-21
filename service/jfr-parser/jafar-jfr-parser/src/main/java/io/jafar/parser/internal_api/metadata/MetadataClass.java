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

import io.jafar.parser.ParsingUtils;
import io.jafar.parser.ValueLoader;
import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.util.*;

public final class MetadataClass extends AbstractMetadataElement {
    private static final Set<String> primitiveTypeNames = Set.of("byte", "char", "short", "int", "long", "float", "double", "boolean", "java.lang.String");
    private final Map<String, io.jafar.parser.internal_api.metadata.MetadataSetting> settings = new HashMap<>();
    private final List<io.jafar.parser.internal_api.metadata.MetadataAnnotation> annotations = new ArrayList<>();
    private final Map<String, io.jafar.parser.internal_api.metadata.MetadataField> fieldMap = new HashMap<>();
    private final List<io.jafar.parser.internal_api.metadata.MetadataField> fields = new ArrayList<>();

    private final long id;
    private final String superType;
    private final boolean isSimpleType;
    private final boolean isPrimitive;
    private final boolean simple;

    final io.jafar.parser.internal_api.metadata.ExceptionalConsumer<RecordingStream, IOException> skipInstruction;

    MetadataClass(RecordingStream stream, io.jafar.parser.internal_api.metadata.ElementReader reader) throws IOException {
        super(stream, MetadataElementKind.CLASS);
        this.id = Long.parseLong(getAttribute("id"));
        this.superType = getAttribute("superType");
        this.isSimpleType = Boolean.parseBoolean(getAttribute("simpleType"));
        this.isPrimitive = primitiveTypeNames.contains(getName());
        this.simple = isSimpleType || isPrimitive(name);
        metadataLookup.addClass(id, this);
        resetAttributes();
        readSubelements(reader);
        // must be the last instruction such that all fields are resolved
        this.skipInstruction = getSkipInstruction(this);
    }

    public long getId() {
        return id;
    }

    public String getSuperType() {
        return superType;
    }

    public boolean isSimple() {
        return simple;
    }

    public boolean isPrimitive() {
        return isPrimitive;
    }

    protected void onSubelement(AbstractMetadataElement element) {
        if (element.getKind() == MetadataElementKind.SETTING) {
            io.jafar.parser.internal_api.metadata.MetadataSetting setting = (io.jafar.parser.internal_api.metadata.MetadataSetting) element;
            settings.put(setting.getName(), setting);
        } else if (element.getKind() == MetadataElementKind.ANNOTATION) {
            annotations.add((MetadataAnnotation) element);
        } else if (element.getKind() == MetadataElementKind.FIELD) {
            io.jafar.parser.internal_api.metadata.MetadataField field = (io.jafar.parser.internal_api.metadata.MetadataField) element;
            fieldMap.put(field.getName(), field);
            fields.add(field);
        } else {
            throw new IllegalStateException("Unexpected subelement: " + element.getKind());
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visitClass(this);
        settings.values().forEach(s -> s.accept(visitor));
        annotations.forEach(a -> a.accept(visitor));
        fieldMap.values().forEach(f -> f.accept(visitor));
        visitor.visitEnd(this);
    }

    public List<io.jafar.parser.internal_api.metadata.MetadataField> getFields() {
        return Collections.unmodifiableList(fields);
    }

    public Set<String> getFieldNames() {
        return fieldMap.keySet();
    }

    public void skip(RecordingStream stream) throws IOException {
        skipInstruction.accept(stream);
    }

    private static boolean isPrimitive(String typeName) {
        return typeName.equals("byte") ||
                typeName.equals("short") ||
                typeName.equals("char") ||
                typeName.equals("int") ||
                typeName.equals("long") ||
                typeName.equals("float") ||
                typeName.equals("double") ||
                typeName.equals("boolean") ||
                typeName.equals("java.lang.String");
    }

    private static ExceptionalConsumer<RecordingStream, IOException> getSkipInstruction(MetadataClass typeDescriptor) {
        switch (typeDescriptor.getName()) {
            case "byte", "boolean": {
                return RecordingStream::read;
            }
            case "short", "char", "int", "long": {
                return RecordingStream::readVarint;
            }
            case "float": {
                return RecordingStream::readFloat;
            }
            case "double": {
                return RecordingStream::readDouble;
            }
            case "java.lang.String":
                return ParsingUtils::skipUTF8;
            default: {
                if (typeDescriptor.getFields().size() == 1) {
                    io.jafar.parser.internal_api.metadata.MetadataField field = typeDescriptor.getFields().getFirst();
                    if (field.hasConstantPool()) {
                        return RecordingStream::readVarint;
                    }
                    return stream -> {
                        MetadataClass fieldType = field.getType();
                        fieldType.skip(stream);
                    };
                }
                List<io.jafar.parser.internal_api.metadata.MetadataField> fields = typeDescriptor.getFields();
                return stream -> {
                    for (int i = 0; i < fields.size(); i++) {
                        MetadataField fld = fields.get(i);
                        ValueLoader.skip(stream, fld.getType(), fld.getDimension() > 0, fld.hasConstantPool());
                    }
                };
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetadataClass that = (MetadataClass) o;
        return id == that.id && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }

    @Override
    public String toString() {
        return "MetadataClass{" +
                "id='" + id + '\'' +
                ", name='" + getName() + "'" +
                ", superType='" + superType + '\'' +
                ", isSimpleType=" + isSimpleType +
                '}';
    }
}
