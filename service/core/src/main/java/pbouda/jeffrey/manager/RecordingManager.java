package pbouda.jeffrey.manager;

import pbouda.jeffrey.repository.model.AvailableRecording;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface RecordingManager {

    List<AvailableRecording> all();

    void upload(String filename, InputStream input) throws IOException;

    void delete(String filename);
}
