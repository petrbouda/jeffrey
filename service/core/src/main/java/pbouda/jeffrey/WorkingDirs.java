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

package pbouda.jeffrey;

import pbouda.jeffrey.common.Json;
import pbouda.jeffrey.repository.model.ProfileInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class WorkingDirs {

    public static final String PROFILE_INFO_JSON = "profile_info.json";
    public static final String RECORDING_JFR = "recording.jfr";
    public static final String PROFILE_DB_FILE = "profile.db";
    public static final String EXPORTS_DIR = "exports";
    private final Path jeffreyDir;
    private final Path recordingsDir;
    private final Path workspaceDir;

    public WorkingDirs(Path jeffreyDir) {
        this.jeffreyDir = jeffreyDir;
        this.recordingsDir = jeffreyDir.resolve("recordings");
        this.workspaceDir = jeffreyDir.resolve("workspace");
    }

    public void initializeDirectories() {
        createDirectories(jeffreyDir);
        createDirectories(recordingsDir);
        createDirectories(workspaceDir);
    }

    public Path recordingsDir() {
        return recordingsDir;
    }

    public Path workspaceDir() {
        return workspaceDir;
    }

    public Path profileDir(String profileId) {
        return workspaceDir.resolve(profileId);
    }

    public Path exportsDir(ProfileInfo profileInfo) {
        return workspaceDir.resolve(profileInfo.id()).resolve(EXPORTS_DIR);
    }

    public Path profileRecording(ProfileInfo profileInfo) {
        return workspaceDir.resolve(profileInfo.id()).resolve(RECORDING_JFR);
    }

    public Path profileDbFile(ProfileInfo profileInfo) {
        return workspaceDir.resolve(profileInfo.id()).resolve(PROFILE_DB_FILE);
    }

    /**
     * Creates a new Profile Hierarchy and returns a {@link Path} to a root profile's directory.
     *
     * @param profileId ID of the provided profile
     * @return a root directory of the profile.
     */
    public Path createProfileHierarchy(String profileId) {
        try {
            Path profileDir = Files.createDirectory(workspaceDir.resolve(profileId));
            Files.createDirectory(profileDir.resolve(EXPORTS_DIR));
            return profileDir;
        } catch (IOException e) {
            throw new RuntimeException("Cannot create a Folder Hierarchy fir a new Profile: " + profileId, e);
        }
    }

    /**
     * Creates a new file of `Profile Info` and dumps profile's information in JSON format.
     *
     * @param profileInfo profile's information
     * @return path to the newly generated profile info file.
     */
    public Path createProfileInfo(ProfileInfo profileInfo) {
        Path profileInfoPath = profileDir(profileInfo.id()).resolve(PROFILE_INFO_JSON);
        try {
            return Files.writeString(profileInfoPath, Json.toPrettyString(profileInfo));
        } catch (IOException e) {
            throw new RuntimeException("Cannot create a Profile Info file: profile_id=" + profileInfo.id(), e);
        }
    }

    /**
     * Copies a used recording to create a new profile. The recording is copied to the folder that belongs to
     * the profile.
     *
     * @param profileId profile's information
     * @param filename  name of the recording file
     * @return path to a copied recording file.
     */
    public Path copyRecording(String profileId, String filename) {
        Path recording = recordingsDir.resolve(filename);
        Path newRecording = profileDir(profileId).resolve(RECORDING_JFR);
        try {
            return Files.copy(recording, newRecording);
        } catch (IOException e) {
            throw new RuntimeException("Cannot copy a recording: source=" + recording + " target=" + newRecording, e);
        }
    }

    /**
     * Iterates over the `workspace` directory and finds all current profiles and its {@link #PROFILE_INFO_JSON} files.
     *
     * @return a collection of profile's info files.
     */
    public List<ProfileInfo> retrieveAllProfiles() {
        try (Stream<Path> paths = Files.list(workspaceDir)) {
            return paths.filter(Files::isDirectory)
                    .map(p -> Json.read(p.resolve(PROFILE_INFO_JSON), ProfileInfo.class))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Cannot read all profiles", e);
        }
    }

    /**
     * Retrieve a single Profile Info using {@link #PROFILE_INFO_JSON}.
     *
     * @return profile info loaded from disk.
     */
    public ProfileInfo retrieveProfileInfo(String profileId) {
        Path profileInfoFile = workspaceDir.resolve(profileId).resolve(PROFILE_INFO_JSON);
        return Json.read(profileInfoFile, ProfileInfo.class);
    }

    /**
     * Remove a profile's directory that deletes all information about the profile itself.
     *
     * @param profileId profile's information
     */
    public void deleteProfile(String profileId) {
        removeDirectory(workspaceDir.resolve(profileId));
    }

    private static void createDirectories(Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot create parent directories: " + path);
        }
    }

    private static void removeDirectory(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            files.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            throw new RuntimeException("Cannot complete removing of a directory: " + directory, e);
        }
    }
}
