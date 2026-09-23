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

package cafe.jeffrey.jib.payload;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What to bake into one image.
 *
 * <p>The provisioner is not optional and so is not a field: it is Jeffrey's own binary, the
 * protocol it writes is the one Jeffrey Hub reads, and an image that carried someone else's copy
 * would drift from that protocol with nothing recording which one it had. Only async-profiler,
 * a third-party library an image may legitimately already ship, can be left out.
 *
 * @param descriptor    the payload jar found on the class path, which decides the provisioner build
 * @param architectures the Linux architectures the build plan targets, in declaration order
 * @param bakeProfiler  false when the build configuration already names a profiler path; setting
 *                      {@code profilerPath} has always meant "use my async-profiler" and still does
 */
public record PayloadPlan(PayloadDescriptor descriptor, Set<String> architectures, boolean bakeProfiler) {

    public PayloadPlan {
        if (descriptor == null) {
            throw new IllegalArgumentException("Payload descriptor must not be null");
        }
        if (architectures == null || architectures.isEmpty()) {
            throw new IllegalArgumentException("At least one target architecture is required");
        }
        architectures = new LinkedHashSet<>(architectures);
    }
}
