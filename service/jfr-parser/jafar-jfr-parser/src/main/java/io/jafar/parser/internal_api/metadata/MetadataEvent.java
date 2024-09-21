

package io.jafar.parser.internal_api.metadata;

import io.jafar.parser.AbstractEvent;
import io.jafar.parser.MutableMetadataLookup;
import io.jafar.parser.ParsingUtils;
import io.jafar.parser.internal_api.RecordingStream;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * JFR Chunk metadata
 *
 * <p>It contains the chunk specific type specifications
 */
public final class MetadataEvent extends AbstractEvent {

  public final int size;
  public final long startTime;
  public final long duration;
  public final long metadataId;
  private final MetadataRoot root;
  private final Set<MetadataClass> classes = new HashSet<>();

  private final boolean forceConstantPools;

  public MetadataEvent(RecordingStream stream, boolean forceConstantPools) throws IOException {
    super(stream);
    size = (int) stream.readVarint();
    if (size == 0) {
      throw new IOException("Unexpected event size. Should be > 0");
    }
    long typeId = stream.readVarint();
    if (typeId != 0) {
      throw new IOException("Unexpected event type: " + typeId + " (should be 0)");
    }
    startTime = stream.readVarint();
    duration = stream.readVarint();
    metadataId = stream.readVarint();
    this.forceConstantPools = forceConstantPools;

    readStringTable(stream);
    root = (MetadataRoot) readElement(stream);
  }

  private void readStringTable(RecordingStream stream) throws IOException {
    int stringCnt = (int) stream.readVarint();
    String[] stringConstants = new String[stringCnt];
    for (int stringIdx = 0; stringIdx < stringCnt; stringIdx++) {
      stringConstants[stringIdx] = ParsingUtils.readUTF8(stream);
    }
    ((MutableMetadataLookup)stream.getContext().getMetadataLookup()).setStringtable(stringConstants);
  }

  private AbstractMetadataElement readElement(RecordingStream stream) throws IOException {
    try {
      // get the element name
      int stringPtr = (int) stream.readVarint();
      String typeId = stream.getContext().getMetadataLookup().getString(stringPtr);
      AbstractMetadataElement element = switch (typeId) {
        case "class" -> new MetadataClass(stream, this::readElement);
        case "field" -> new MetadataField(stream, this::readElement, forceConstantPools);
        case "annotation" -> new MetadataAnnotation(stream, this::readElement);
        case "root" -> new MetadataRoot(stream, this::readElement);
        case "metadata" -> new MetadataElement(stream, this::readElement);
        case "region" -> new MetadataRegion(stream, this::readElement);
        case "setting" -> new MetadataSetting(stream, this::readElement);
        default -> {
          throw new IOException("Unsupported metadata type: " + typeId);
        }
      };

      if ("class".equals(typeId)) {
        classes.add((MetadataClass) element);
      }
      return element;
    } catch (Throwable t) {
      t.printStackTrace();
      throw t;
    }
  }

  public MetadataRoot getRoot() {
    return root;
  }

  public Set<MetadataClass> getClasses() {
    return Collections.unmodifiableSet(classes);
  }

  @Override
  public String toString() {
    return "Metadata{"
        + "size="
        + size
        + ", startTime="
        + startTime
        + ", duration="
        + duration
        + ", metadataId="
        + metadataId
        + '}';
  }
}
