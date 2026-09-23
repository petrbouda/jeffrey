/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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
