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

export interface GCConfigurationData {
  detectedType: string;
  collector: GCConfiguration;
  heap: GCHeapConfiguration;
  threads: GCThreadConfiguration;
  survivor: GCSurvivorConfiguration;
  tlab: GCTLABConfiguration;
  youngGeneration: GCYoungGenerationConfiguration;
}

export interface GCConfiguration {
  youngCollector: string;
  oldCollector: string;
  explicitGCConcurrent: boolean;
  explicitGCDisabled: boolean;
  pauseTarget: number;
}

export interface GCHeapConfiguration {
  minSize: number;
  maxSize: number;
  initialSize: number;
  usesCompressedOops: boolean;
  compressedOopsMode: string;
  objectAlignment: number;
  heapAddressBits: number;
}

export interface GCThreadConfiguration {
  parallelGCThreads: number;
  concurrentGCThreads: number;
  usesDynamicGCThreads: boolean;
}

export interface GCSurvivorConfiguration {
  maxTenuringThreshold: number;
  initialTenuringThreshold: number;
}

export interface GCTLABConfiguration {
  usesTLABs: boolean;
  minTLABSize: number;
  tlabRefillWasteLimit: number;
}

export interface GCYoungGenerationConfiguration {
  maxSize: number;
  minSize: number;
  newRatio: number;
}

export default GCConfigurationData;
