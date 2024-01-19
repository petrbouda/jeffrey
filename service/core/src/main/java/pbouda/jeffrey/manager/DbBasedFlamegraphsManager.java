package pbouda.jeffrey.manager;

import pbouda.jeffrey.repository.FlamegraphInfo;
import pbouda.jeffrey.repository.FlamegraphRepository;
import pbouda.jeffrey.repository.ProfileInfo;

import java.util.List;
import java.util.Optional;

public class DbBasedFlamegraphsManager implements FlamegraphsManager {

    private final ProfileInfo profileInfo;
    private final FlamegraphRepository flamegraphRepository;

    public DbBasedFlamegraphsManager(ProfileInfo profileInfo, FlamegraphRepository flamegraphRepository) {
        this.profileInfo = profileInfo;
        this.flamegraphRepository = flamegraphRepository;
    }

    @Override
    public List<FlamegraphInfo> all() {
        return flamegraphRepository.all(profileInfo.id());
    }

    @Override
    public Optional<byte[]> content(String flamegraphId) {
        return Optional.ofNullable(flamegraphRepository.content(profileInfo.id(), flamegraphId));
    }

    @Override
    public void upload(FlamegraphInfo flamegraphInfo, byte[] content) {
        flamegraphRepository.insert(flamegraphInfo, content);
    }

    @Override
    public void delete(String flamegraphId) {
        flamegraphRepository.delete(profileInfo.id(), flamegraphId);
    }
}
