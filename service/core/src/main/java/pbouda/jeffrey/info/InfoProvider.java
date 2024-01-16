package pbouda.jeffrey.info;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;

public interface InfoProvider {

    void initialize();

    ProfileInfo generateProfile(Path profilePath);

    void removeProfile(String profileId);

    Optional<ProfileInfo> getProfile(String profileId);

    Collection<ProfileInfo> profiles();

}
