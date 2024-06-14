package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.WorkingDirs;
import pbouda.jeffrey.jfr.configuration.ProfileInformationProvider;
import pbouda.jeffrey.repository.CacheKey;
import pbouda.jeffrey.repository.CacheRepository;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.util.Optional;

public class DbBasedProfileInfoManager implements ProfileInfoManager {

    private final ProfileInformationProvider infoProvider;
    private final ProfileInfo profileInfo;
    private final CacheRepository cacheRepository;

    public DbBasedProfileInfoManager(
            ProfileInfo profileInfo,
            WorkingDirs workingDirs,
            CacheRepository cacheRepository) {

        this.profileInfo = profileInfo;
        this.cacheRepository = cacheRepository;
        this.infoProvider = new ProfileInformationProvider(workingDirs.profileRecording(profileInfo));
    }

    @Override
    public JsonNode information() {
        Optional<JsonNode> infoOpt = cacheRepository.get(CacheKey.INFO);

        if (infoOpt.isPresent()) {
            return infoOpt.get();
        } else {
            ObjectNode jsonContent = infoProvider.get();
            cacheRepository.insert(CacheKey.INFO, jsonContent);
            return jsonContent;
        }
    }
}
