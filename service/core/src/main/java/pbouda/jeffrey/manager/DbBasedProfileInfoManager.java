package pbouda.jeffrey.manager;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.jfr.configuration.ProfileInformationProvider;
import pbouda.jeffrey.repository.CommonRepository;
import pbouda.jeffrey.repository.ProfileInfo;

import java.nio.charset.Charset;
import java.util.Optional;

public class DbBasedProfileInfoManager implements ProfileInfoManager {

    private final ProfileInformationProvider infoProvider;
    private final ProfileInfo profileInfo;
    private final CommonRepository commonRepository;

    public DbBasedProfileInfoManager(
            ProfileInfo profileInfo,
            CommonRepository commonRepository) {

        this.profileInfo = profileInfo;
        this.commonRepository = commonRepository;
        this.infoProvider = new ProfileInformationProvider(profileInfo.recordingPath());
    }

    @Override
    public byte[] information() {
        Optional<byte[]> content = commonRepository.selectInformation(profileInfo.id());
        if (content.isPresent()) {
            return content.get();
        } else {
            ObjectNode jsonContent = infoProvider.get();
            commonRepository.insertInformation(profileInfo.id(), jsonContent);
            return jsonContent.toString().getBytes(Charset.defaultCharset());
        }
    }

    @Override
    public void cleanup() {
        commonRepository.deleteInformation(profileInfo.id());
    }
}
