package pbouda.jeffrey.manual;

import pbouda.jeffrey.shared.filesystem.JeffreyDirs;
import pbouda.jeffrey.profile.parser.JfrRecordingInformationParser;
import pbouda.jeffrey.provider.api.model.recording.RecordingInformation;

import java.nio.file.Path;

public class RecordingInfoParser {

    private static final Path JFRS_DIR = Path.of("manual-tests/jfrs");
    private static final JeffreyDirs JEFFREY_DIRS = new JeffreyDirs(null, JFRS_DIR);

    static void main() {
        System.out.println("Hello World!");

        JfrRecordingInformationParser parser = new JfrRecordingInformationParser(JEFFREY_DIRS);
        RecordingInformation provide = parser.provide(Path.of("manual-tests/jfrs/profile.jfr.lz4"));
        System.out.println(provide);
    }
}
