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

package cafe.jeffrey.jib.payload;

/**
 * A payload artifact could not be fetched or unpacked at build time.
 *
 * <p>Checked on purpose. The runtime fail-open guarantee covers container start — a JVM that cannot
 * be profiled must still boot. It says nothing about the build, where silently producing an image
 * without a profiler is the failure this whole mechanism exists to remove. Making the exception
 * checked forces every caller to decide, and the only correct decision is to fail the build.
 */
public final class PayloadResolutionException extends Exception {

    public PayloadResolutionException(ArtifactCoordinates coordinates, String reason) {
        super(message(coordinates, reason));
    }

    public PayloadResolutionException(ArtifactCoordinates coordinates, String reason, Throwable cause) {
        super(message(coordinates, reason), cause);
    }

    private static String message(ArtifactCoordinates coordinates, String reason) {
        return "Failed to resolve the Jeffrey payload artifact " + coordinates + ": " + reason;
    }
}
