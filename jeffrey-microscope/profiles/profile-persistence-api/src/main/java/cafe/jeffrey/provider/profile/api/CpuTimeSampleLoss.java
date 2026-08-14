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

package cafe.jeffrey.provider.profile.api;

/**
 * How much of the JDK CPU-time sampler's output actually reached the recording.
 * <p>
 * The sampler queues a sample from a signal handler and the JVM drains that queue asynchronously.
 * When the queue overflows, the dropped samples are not silently forgotten — the JVM reports them
 * as {@code jdk.CPUTimeSamplesLost} events, each carrying a {@code lostSamples} count. A flamegraph
 * built from {@code jdk.CPUTimeSample} therefore shows {@link #capturedSamples()} out of
 * {@code capturedSamples + lostSamples}, and the missing ones are not distributed evenly — a thread
 * that burns CPU in bursts loses more than a steady one.
 *
 * @param capturedSamples {@code jdk.CPUTimeSample} events present in the profile
 * @param lostSamples     samples the JVM reported as dropped, summed over all loss events
 * @param lossEvents      how many {@code jdk.CPUTimeSamplesLost} events carried those drops
 */
public record CpuTimeSampleLoss(long capturedSamples, long lostSamples, long lossEvents) {

    public static final CpuTimeSampleLoss EMPTY = new CpuTimeSampleLoss(0, 0, 0);

    public CpuTimeSampleLoss {
        if (capturedSamples < 0 || lostSamples < 0 || lossEvents < 0) {
            throw new IllegalArgumentException(
                    "Sample counts cannot be negative: capturedSamples=" + capturedSamples
                            + " lostSamples=" + lostSamples + " lossEvents=" + lossEvents);
        }
    }

}
