package pbouda.jeffrey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import pbouda.jeffrey.manager.ProfilesManager;
import pbouda.jeffrey.manager.RecordingManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record CommandLineRecordingUploader(Path recordingsDir) implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(CommandLineRecordingUploader.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        var context = event.getApplicationContext();
        var recordingManager = context.getBean(RecordingManager.class);
        var profilesManager = context.getBean(ProfilesManager.class);

        try (var fileStream = Files.list(recordingsDir)) {
            fileStream.forEach(recording -> {
                if (!validRecordingName(recording)) {
                    return;
                }

                String filename = recording.getFileName().toString();
                try {
                    recordingManager.upload(filename, Files.newInputStream(recording));
                    profilesManager.createProfile(filename, true);
                } catch (Exception e) {
                    LOG.error("Cannot upload recording: file={}", recording.getFileName().toString(), e);
                }
                LOG.info("Uploaded and initialized recording: {}", filename);
            });
        } catch (IOException e) {
            LOG.error("Cannot upload recording: error={}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static boolean validRecordingName(Path recording) {
        return !Files.isDirectory(recording)
                && recording.toString().endsWith(".jfr");
    }
}
