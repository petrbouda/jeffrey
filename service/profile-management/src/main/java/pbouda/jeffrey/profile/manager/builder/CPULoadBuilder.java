/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.profile.manager.builder;

import com.fasterxml.jackson.databind.node.ObjectNode;
import pbouda.jeffrey.provider.api.builder.RecordBuilder;
import pbouda.jeffrey.profile.manager.model.thread.ThreadCpuLoads;
import pbouda.jeffrey.profile.manager.model.thread.ThreadWithCpuLoad;
import pbouda.jeffrey.provider.api.repository.model.GenericRecord;

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
