package cafe.jeffrey.manual;

import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import cafe.jeffrey.profile.parser.JfrRecordingInformationParser;
import cafe.jeffrey.provider.profile.model.recording.RecordingInformation;

import java.nio.file.Path;

public class RecordingInfoParser {

    private static final Path JFRS_DIR = Path.of("manual-tests/jfrs");
    private static final TempDirFactory TEMP_DIR_FACTORY = TempDirFactory.of(JFRS_DIR);

    static void main() {
        System.out.println("Hello World!");

        JfrRecordingInformationParser parser = new JfrRecordingInformationParser(TEMP_DIR_FACTORY);
        RecordingInformation provide = parser.provide(Path.of("manual-tests/jfrs/profile.jfr.lz4"));
        System.out.println(provide);
    }
}
