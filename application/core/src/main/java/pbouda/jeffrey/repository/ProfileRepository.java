package pbouda.jeffrey.repository;

import java.util.List;

public interface ProfileRepository {

    /**
     * Retrieve all profiles that are available in the given implementation of repository.
     *
     * @return a collection of all profiles.
     */
    List<Profile> list();

}
