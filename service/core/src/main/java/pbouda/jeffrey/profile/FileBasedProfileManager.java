package pbouda.jeffrey.profile;

import pbouda.jeffrey.FileUtils;
import pbouda.jeffrey.Naming;
import pbouda.jeffrey.flamegraph.EventType;
import pbouda.jeffrey.info.ProfileInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.*;

public class FileBasedProfileManager implements ProfileManager {

    private final ProfileInfo profileInfo;

    public FileBasedProfileManager(ProfileInfo profileInfo) {
        this.profileInfo = profileInfo;
    }

    @Override
    public OutputStream uploadFlamegraph(EventType eventType) {
        return _uploadFlamegraph(Path.of(Naming.FLAMEGRAPHS_OVERALL_DIR_NAME, eventType.name().toLowerCase()));
    }

    @Override
    public OutputStream uploadPartialFlamegraph(String filename) {
        return _uploadFlamegraph(Path.of(Naming.FLAMEGRAPHS_PARTIAL_DIR_NAME, filename));
    }

    private OutputStream _uploadFlamegraph(Path pathFromDataDir) {
        Path target = profileInfo.dataDir()
                .resolve(Naming.FLAMEGRAPHS_DIR_NAME)
                .resolve(pathFromDataDir);

        FileUtils.createDirectoriesForFile(target);
        try {
            return Channels.newOutputStream(FileChannel.open(target, CREATE, WRITE, TRUNCATE_EXISTING));
        } catch (IOException e) {
            throw new RuntimeException("Cannot open the stream for uploading a file: " + target, e);
        }
    }

    @Override
    public OutputStream uploadHeatmap() {
        return null;
    }
}
