/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pbouda.jeffrey.profile.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.common.model.ProfileInfo;
import pbouda.jeffrey.provider.api.ProfileInitializer;
import pbouda.jeffrey.provider.api.repository.Repositories;

import java.time.Duration;

/**
 * Implementation of ProfileParser that uses the provider-api to parse
 * JFR recordings and store events in the database.
 */
public class ProfileParserImpl implements ProfileParser {

    private static final Logger LOG = LoggerFactory.getLogger(ProfileParserImpl.class);

    private final Repositories repositories;
    private final ProfileInitializer profileInitializer;

    public ProfileParserImpl(
            Repositories repositories,
            ProfileInitializer profileInitializer) {
        this.repositories = repositories;
        this.profileInitializer = profileInitializer;
    }

    @Override
    public ProfileInfo parse(String recordingId) {
        long start = System.nanoTime();

        // Parse the recording and create profile in database
        String profileId = profileInitializer.newProfile(recordingId);

        // Retrieve the profile info from the repository
        ProfileInfo profileInfo = repositories.newProfileRepository(profileId).find()
                .orElseThrow(() -> new RuntimeException("Could not find newly created profile: " + profileId));

        long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
        LOG.info("Profile parsed and stored: profile_id={} profile_name={} elapsed_ms={}",
                profileInfo.id(), profileInfo.name(), millis);

        return profileInfo;
    }
}
