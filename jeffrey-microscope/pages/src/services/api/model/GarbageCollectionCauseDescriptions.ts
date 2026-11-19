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

interface GCCauseInfo {
  description: string;
}

const gcCauseDescriptions: Record<string, GCCauseInfo> = {
  'System.gc()': {
    description:
      'GC was explicitly triggered by calling System.gc() or Runtime.getRuntime().gc(). This forces a full stop-the-world garbage collection cycle.'
  },

  'Allocation Failure': {
    description:
      'Most common GC cause. Triggered when there is insufficient space to allocate new objects in the young generation.'
  },

  'Metadata GC Threshold': {
    description:
      'Triggered when the Metaspace (class metadata storage) reaches its threshold and needs to be cleaned up.'
  },

  Ergonomics: {
    description:
      "GC triggered by the JVM's automatic tuning system to optimize performance based on application behavior and system resources."
  },

  'G1 Evacuation Pause': {
    description:
      'Standard young generation collection in G1GC where live objects are evacuated (copied) from source regions to destination regions.'
  },

  'G1 Humongous Allocation': {
    description:
      'Triggered when allocating objects larger than 50% of G1 region size (humongous objects), which are allocated directly in old generation.'
  },

  'Last Ditch Collection': {
    description:
      'Emergency full GC performed when G1 runs out of memory during evacuation and cannot complete normal collection cycles.'
  },

  'Concurrent Mark Start': {
    description:
      'G1 initiates concurrent marking phase when old generation occupancy reaches threshold (default 45%) to identify live objects.'
  },

  'Concurrent Mode Failure': {
    description:
      'CMS collector failed to complete concurrent collection before heap became full, forcing a stop-the-world full GC.'
  },

  'Promotion Failed': {
    description:
      'Objects cannot be promoted from young to old generation due to fragmentation or insufficient space in old generation.'
  },

  'To-space Exhausted': {
    description:
      'G1 runs out of evacuation space during young generation collection, unable to copy all live objects to destination regions.'
  },

  'GCLocker Initiated GC': {
    description:
      'GC was deferred while threads were in JNI critical regions, then triggered when the lock was released.'
  },

  'Heap Inspection/Dump': {
    description:
      'GC triggered by heap analysis tools or heap dump operations to ensure consistent heap state for inspection.'
  },

  Warmup: {
    description:
      'GC triggered during application startup phase, often part of JVM initialization or application warmup procedures.'
  },

  Timer: {
    description:
      'GC triggered by timer-based mechanisms, often related to RMI periodic cleanup or other scheduled maintenance activities.'
  },

  'Diagnostic Command': {
    description:
      'GC triggered by diagnostic commands like jcmd, often for heap analysis or system diagnostics.'
  },

  'JFR Periodic': {
    description:
      'GC triggered by Java Flight Recorder when heap statistics collection is enabled, ensuring consistent heap snapshots.'
  },

  Proactive: {
    description:
      'GC proactively triggered to prevent future allocation failures or optimize memory usage before reaching critical thresholds.'
  },

  'Metadata GC Clear Soft References': {
    description:
      'Metaspace cleanup that also clears soft references to reclaim maximum possible memory from class metadata and associated objects.'
  }
};

export class GarbageCollectionCauseDescriptions {
  static getDescription(cause: string): GCCauseInfo | null {
    return gcCauseDescriptions[cause] || null;
  }

  static getTooltipContent(cause: string): string {
    const info = this.getDescription(cause);
    if (!info) {
      return `Unknown GC cause: ${cause}`;
    }

    return info.description;
  }

  static getAllCauses(): Array<{ name: string; description: string }> {
    return Object.entries(gcCauseDescriptions).map(([name, info]) => ({
      name,
      description: info.description
    }));
  }
}
