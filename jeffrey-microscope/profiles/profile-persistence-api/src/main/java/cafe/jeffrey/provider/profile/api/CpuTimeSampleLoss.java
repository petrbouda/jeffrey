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
