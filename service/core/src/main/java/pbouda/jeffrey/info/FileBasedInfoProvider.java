package pbouda.jeffrey.info;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

public class FileBasedInfoProvider implements InfoProvider, Closeable {

    private static final String INIT_CONTENT = """
            {
                "profiles": []
            }""";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final Path USER_HOME_DIR = Path.of(System.getProperty("user.home"));

    private final StringDatabase database;

    public FileBasedInfoProvider() {
        this(USER_HOME_DIR.resolve(".jeffrey"));
    }

    public FileBasedInfoProvider(Path homeDir) {
        this.database = new FileBasedStringDatabase(homeDir.resolve("profiles.json"));
    }

    @Override
    public void initialize() {
        Runtime.getRuntime().addShutdownHook(new Thread(database::close));
        database.initialize();
        database.writeContent(INIT_CONTENT);
    }

    @Override
    public ProfileInfo generateProfile(Path profilesPath) {
        ProfileInfo profileInfo = new ProfileInfo(
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                profilesPath);

        InfoFile infoFile = readInfo();
        infoFile.addProfile(profileInfo);
        writeInfo(infoFile);

        return profileInfo;
    }


    @Override
    public void removeProfile(String profileId) {
        InfoFile infoFile = readInfo();
        infoFile.removeProfile(profileId);
        writeInfo(infoFile);
    }

    @Override
    public Optional<ProfileInfo> getProfile(String profileId) {
        return readInfo().profiles.stream()
                .filter(profile -> profile.id().equals(profileId))
                .findFirst();
    }

    @Override
    public Collection<ProfileInfo> profiles() {
        return null;
    }

    private InfoFile readInfo() {
        try {
            String content = database.readContent();
            return MAPPER.readValue(content, InfoFile.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot parse a JSON from an info file", e);
        }
    }

    private void writeInfo(InfoFile infoFile) {
        try {
            database.writeContent(MAPPER.writeValueAsString(infoFile));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot write info file into JSON", e);
        }
    }

    @Override
    public void close() throws IOException {
        database.close();
    }

    public static class InfoFile {

        private List<ProfileInfo> profiles;

        @JsonCreator
        public InfoFile(
                @JsonProperty("profiles") List<ProfileInfo> profiles) {
            this.profiles = profiles;
        }

        public void addProfile(ProfileInfo profileInfo) {
            List<ProfileInfo> profiles = new ArrayList<>(this.profiles);
            profiles.add(profileInfo);
            this.profiles = profiles;
        }

        public void removeProfile(String id) {
            this.profiles = profiles.stream()
                    .filter(profile -> !profile.id().equals(id))
                    .toList();
        }
    }
}
