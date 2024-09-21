

package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.internal_api.RecordingStream;
import io.jafar.parser.internal_api.metadata.AbstractMetadataElement;

import java.io.IOException;

@FunctionalInterface
interface ElementReader {
    AbstractMetadataElement readElement(RecordingStream stream) throws IOException;
}
