

package io.jafar.parser;

import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.parser.internal_api.metadata.MetadataClass;

import java.io.IOException;

public final class ValueLoader {
    public static void skip(RecordingStream stream, MetadataClass typeDescriptor, boolean isArray, boolean hasConstantPool) throws IOException {
        if (isArray) {
            int len = (int) stream.readVarint();
            for (int i = 0; i < len; i++) {
                skip(stream, typeDescriptor, false, hasConstantPool);
            }
        } else {
            if (hasConstantPool) {
                stream.readVarint();
            } else {
                typeDescriptor.skip(stream);
            }
        }
    }
}
