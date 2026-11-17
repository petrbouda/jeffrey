/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package cafe.jeffrey.profile.manager.thread.builder;

import tools.jackson.databind.node.ObjectNode;
import cafe.jeffrey.provider.profile.api.RecordBuilder;
import cafe.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import cafe.jeffrey.profile.manager.model.thread.ThreadWithCpuLoad;
import cafe.jeffrey.provider.profile.api.GenericRecord;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class CPULoadBuilder implements RecordBuilder<GenericRecord, ThreadCpuLoads> {

    private final int limit;

    private final PriorityQueue<ThreadWithCpuLoad> topSystemCpuLoads;
    private final PriorityQueue<ThreadWithCpuLoad> topUserCpuLoads;

    public CPULoadBuilder(int limit) {
        this.limit = limit;

        // Min heaps to keep the highest values by removing the smallest when full
        this.topSystemCpuLoads = new PriorityQueue<>(Comparator.comparing(ThreadWithCpuLoad::cpuLoad));
        this.topUserCpuLoads = new PriorityQueue<>(Comparator.comparing(ThreadWithCpuLoad::cpuLoad));

    }

    @Override
    public void onRecord(GenericRecord record) {
        ObjectNode jsonNodes = record.jsonFields();
        long timestamp = record.startTimestamp().toEpochMilli();
        double systemCpuLoad = jsonNodes.get("system").asDouble();
        double userCpuLoad = jsonNodes.get("user").asDouble();

        ThreadWithCpuLoad systemThread = new ThreadWithCpuLoad(timestamp, record.threadInfo(), BigDecimal.valueOf(systemCpuLoad));
        ThreadWithCpuLoad userThread = new ThreadWithCpuLoad(timestamp, record.threadInfo(), BigDecimal.valueOf(userCpuLoad));

        // Maintain top system CPU loads
        if (topSystemCpuLoads.size() < limit) {
            topSystemCpuLoads.add(systemThread);
        } else if (systemThread.cpuLoad().compareTo(topSystemCpuLoads.peek().cpuLoad()) > 0) {
            topSystemCpuLoads.poll(); // Remove the smallest element
            topSystemCpuLoads.add(systemThread);
        }

        // Maintain top user CPU loads
        if (topUserCpuLoads.size() < limit) {
            topUserCpuLoads.add(userThread);
        } else if (userThread.cpuLoad().compareTo(topUserCpuLoads.peek().cpuLoad()) > 0) {
            topUserCpuLoads.poll(); // Remove the smallest element
            topUserCpuLoads.add(userThread);
        }
    }

    @Override
    public ThreadCpuLoads build() {
        // Convert priority queues to lists
        List<ThreadWithCpuLoad> userList = new ArrayList<>(topUserCpuLoads);
        List<ThreadWithCpuLoad> systemList = new ArrayList<>(topSystemCpuLoads);

        // Sort lists in descending order of CPU load
        userList.sort(Comparator.comparing(ThreadWithCpuLoad::cpuLoad).reversed());
        systemList.sort(Comparator.comparing(ThreadWithCpuLoad::cpuLoad).reversed());

        return new ThreadCpuLoads(userList, systemList);
    }
}
