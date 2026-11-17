/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.provider.profile.api;

import cafe.jeffrey.shared.persistence.DatabaseManager;

/**
 * Provider for profile-level persistence operations.
 * Manages per-profile databases and event writing.
 */
public interface ProfilePersistenceProvider {

    /**
     * Get the profile database provider for managing per-profile databases.
     *
     * @return profile database provider
     */
    DatabaseManager databaseManager();

    /**
     * Get the factory for creating event writers.
     *
     * @return event writer factory
     */
    EventWriter.Factory eventWriterFactory();

    /**
     * Get the profile repositories factory.
     *
     * @return profile repositories factory
     */
    ProfileRepositories repositories();
}
