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

package pbouda.jeffrey.profile.manager;

import pbouda.jeffrey.shared.common.model.ProfileInfo;

import java.util.function.Function;

public interface ProfileManager {

    @FunctionalInterface
    interface Factory extends Function<ProfileInfo, ProfileManager> {
    }

    ProfileInfo info();

    ProfileConfigurationManager profileConfigurationManager();

    AutoAnalysisManager autoAnalysisManager();

    FlamegraphManager flamegraphManager();

    FlamegraphManager diffFlamegraphManager(ProfileManager secondaryManager);

    SubSecondManager subSecondManager();

    TimeseriesManager timeseriesManager();

    TimeseriesManager diffTimeseriesManager(ProfileManager secondaryManager);

    EventViewerManager eventViewerManager();

    FlagsManager flagsManager();

    ThreadManager threadManager();

    JITCompilationManager jitCompilationManager();

    GuardianManager guardianManager();

    AdditionalFilesManager additionalFilesManager();

    GarbageCollectionManager gcManager();

    ContainerManager containerManager();

    HeapMemoryManager heapMemoryManager();

    HeapDumpManager heapDumpManager();

    ProfileFeaturesManager featuresManager();

    ProfileCustomManager custom();

    ProfileInfo updateName(String name);

    /**
     * Returns the total size of the profile directory in bytes.
     * This includes the DuckDB database and any additional files.
     *
     * @return size in bytes
     */
    long sizeInBytes();

    void delete();
}
