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
package cafe.jeffrey.profile.manager.model.trace;

/**
 * One bar of a run's duration histogram: how many members took between two durations.
 *
 * @param fromNanos where the bucket starts, inclusive
 * @param toNanos   where it ends — inclusive for the last bucket, exclusive for the rest
 * @param count     how many members fell inside it
 */
public record TraceRunDurationBucket(long fromNanos, long toNanos, long count) {
}
