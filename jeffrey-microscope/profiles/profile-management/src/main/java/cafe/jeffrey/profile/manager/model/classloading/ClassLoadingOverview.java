/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.manager.model.classloading;

/**
 * Headline class-loading metrics for a profile.
 *
 * @param currentlyLoaded       loaded minus unloaded classes (live class count)
 * @param totalLoaded           cumulative classes loaded since JVM start
 * @param totalUnloaded         cumulative classes unloaded since JVM start
 * @param classLoaderCount      number of distinct class loaders in the latest snapshot
 * @param metaspaceUsedBytes    metaspace reserved across all class loaders (sum of chunk sizes)
 * @param hiddenClassCount      hidden classes across all class loaders (lambdas, proxies, method handles)
 * @param hasClassLoadEvents    whether per-class {@code jdk.ClassLoad} events are present in the recording
 * @param hasRedefinitionEvents whether {@code jdk.ClassRedefinition} events are present in the recording
 */
public record ClassLoadingOverview(
        long currentlyLoaded,
        long totalLoaded,
        long totalUnloaded,
        int classLoaderCount,
        long metaspaceUsedBytes,
        long hiddenClassCount,
        boolean hasClassLoadEvents,
        boolean hasRedefinitionEvents) {
}
