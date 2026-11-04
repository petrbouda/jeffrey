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

package cafe.jeffrey.profile.heapdump.model;

/**
 * Where the next page of a query walked object by object starts: at the instance
 * {@code instanceId} of the class {@code classId} (or the first after it), passing over the first
 * {@code skip} rows read from there on. A page that ends partway through one object's rows — a
 * graph function fans one object out into many — resumes inside it; one cut short for its size
 * resumes from its own start, passing over the rows it showed.
 *
 * <p>Only a query the engine walks in its instances' order without sorting resumes so; any other
 * pages by offset.
 */
public record OQLResumePoint(long classId, long instanceId, int skip) {

    public OQLResumePoint {
        if (skip < 0) {
            throw new IllegalArgumentException("skip must not be negative: " + skip);
        }
    }

    /** The same point, passing over {@code rows} more rows. */
    public OQLResumePoint passingOver(int rows) {
        return new OQLResumePoint(classId, instanceId, Math.addExact(skip, rows));
    }
}
