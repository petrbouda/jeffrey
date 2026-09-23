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

/**
 * A payload could not be found on the class path or unpacked at build time.
 *
 * <p>Checked on purpose. The runtime fail-open guarantee covers container start — a JVM that cannot
 * be profiled must still boot. It says nothing about the build, where silently producing an image
 * without a profiler is the failure this whole mechanism exists to remove. Making the exception
 * checked forces every caller to decide, and the only correct decision is to fail the build.
 */
public final class PayloadResolutionException extends Exception {

    public PayloadResolutionException(String resource, String reason) {
        super(message(resource, reason));
    }

    public PayloadResolutionException(String resource, String reason, Throwable cause) {
        super(message(resource, reason), cause);
    }

    private static String message(String resource, String reason) {
        return "Failed to load the Jeffrey payload " + resource + ": " + reason;
    }
}
