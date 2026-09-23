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
 * Latest per-class-loader statistics snapshot, derived from {@code jdk.ClassLoaderStatistics}.
 *
 * @param name                 readable loader identity (type, optionally with instance name)
 * @param parentName           readable parent loader identity, or {@code null} for the bootstrap parent
 * @param classCount           number of classes currently loaded by this loader
 * @param metaspaceBytes       metaspace reserved by this loader (chunk size)
 * @param blockBytes           metaspace actually used by this loader (block size)
 * @param hiddenClassCount     hidden classes loaded by this loader
 * @param hiddenMetaspaceBytes metaspace reserved for hidden classes (hidden chunk size)
 */
public record ClassLoaderStat(
        String name,
        String parentName,
        long classCount,
        long metaspaceBytes,
        long blockBytes,
        long hiddenClassCount,
        long hiddenMetaspaceBytes) {
}
