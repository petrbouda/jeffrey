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

// Frontend-only mock for instance detail cards (Instance / JVM·GC Heap / Container).
// Backend wiring will replace this; the shape mirrors jdk.GCHeapConfiguration
// and jdk.ContainerConfiguration events from JFR.

export type InstanceMockData = {
  instance: {
    shortId: string;
    host: string;
    jdk: string;
  };
  jvmGcHeap: {
    minSize: string;
    initialSize: string;
    maxSize: string;
    usesCompressedOops: string;
    compressedOopsMode: string;
    objectAlignment: string;
    heapAddressBits: string;
  };
  container: {
    type: string;
    effectiveCpuCount: string;
    cpuQuota: string;
    cpuSlicePeriod: string;
    cpuShares: string;
    memoryLimit: string;
    memorySoftLimit: string;
    swapMemoryLimit: string;
    hostTotalMemory: string;
  };
};

const HOSTS = [
  'k8s-prod-eu-2/node-17',
  'k8s-prod-eu-1/node-03',
  'k8s-prod-eu-2/node-09',
  'k8s-prod-us-1/node-22',
  'k8s-prod-us-2/node-14'
];

const JDKS = ['OpenJDK 21.0.3', 'OpenJDK 17.0.9', 'OpenJDK 21.0.5', 'GraalVM 21.0.2'];

const HEAP_PROFILES = [
  { initial: '1 GiB', max: '4 GiB' },
  { initial: '512 MiB', max: '2 GiB' },
  { initial: '2 GiB', max: '8 GiB' },
  { initial: '256 MiB', max: '1 GiB' }
];

const CONTAINER_PROFILES = [
  {
    type: 'cgroupv2',
    effectiveCpuCount: '4',
    cpuQuota: '400000 µs',
    memoryLimit: '4 GiB',
    memorySoftLimit: '3.5 GiB',
    hostTotalMemory: '64 GiB'
  },
  {
    type: 'cgroupv2',
    effectiveCpuCount: '2',
    cpuQuota: '200000 µs',
    memoryLimit: '2 GiB',
    memorySoftLimit: '1.75 GiB',
    hostTotalMemory: '64 GiB'
  },
  {
    type: 'cgroupv1',
    effectiveCpuCount: '8',
    cpuQuota: '800000 µs',
    memoryLimit: '16 GiB',
    memorySoftLimit: '14 GiB',
    hostTotalMemory: '128 GiB'
  }
];

function hash(input: string): number {
  let h = 0;
  for (let i = 0; i < input.length; i++) {
    h = (h * 31 + input.charCodeAt(i)) | 0;
  }
  return Math.abs(h);
}

function pick<T>(arr: T[], key: string, salt: number): T {
  return arr[(hash(key) + salt) % arr.length];
}

export function getInstanceMockData(instanceId: string): InstanceMockData {
  const heap = pick(HEAP_PROFILES, instanceId, 0);
  const container = pick(CONTAINER_PROFILES, instanceId, 1);
  return {
    instance: {
      shortId: '#' + instanceId.replace(/-/g, '').slice(-6),
      host: pick(HOSTS, instanceId, 2),
      jdk: pick(JDKS, instanceId, 3)
    },
    jvmGcHeap: {
      minSize: '512 MiB',
      initialSize: heap.initial,
      maxSize: heap.max,
      usesCompressedOops: 'true',
      compressedOopsMode: 'Zero based',
      objectAlignment: '8 B',
      heapAddressBits: '64'
    },
    container: {
      type: container.type,
      effectiveCpuCount: container.effectiveCpuCount,
      cpuQuota: container.cpuQuota,
      cpuSlicePeriod: '100000 µs',
      cpuShares: '1024',
      memoryLimit: container.memoryLimit,
      memorySoftLimit: container.memorySoftLimit,
      swapMemoryLimit: container.memoryLimit,
      hostTotalMemory: container.hostTotalMemory
    }
  };
}
