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
