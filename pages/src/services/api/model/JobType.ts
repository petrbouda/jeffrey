/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

export enum JobType {
    PROJECT_INSTANCE_SESSION_CLEANER = 'PROJECT_INSTANCE_SESSION_CLEANER',
    PROJECT_INSTANCE_RECORDING_CLEANER = 'PROJECT_INSTANCE_RECORDING_CLEANER',
    INTERVAL_RECORDING_GENERATOR = 'INTERVAL_RECORDING_GENERATOR',
    PERIODIC_RECORDING_GENERATOR = 'PERIODIC_RECORDING_GENERATOR',
    COPY_RECORDING_GENERATOR = 'COPY_RECORDING_GENERATOR',
    REPOSITORY_JFR_COMPRESSION = 'REPOSITORY_JFR_COMPRESSION',
    SESSION_FINISHED_DETECTOR = 'SESSION_FINISHED_DETECTOR',
    PROJECT_RECORDING_STORAGE_SYNCHRONIZER = 'PROJECT_RECORDING_STORAGE_SYNCHRONIZER',
    ORPHANED_PROJECT_RECORDING_STORAGE_CLEANER = 'ORPHANED_PROJECT_RECORDING_STORAGE_CLEANER'
}