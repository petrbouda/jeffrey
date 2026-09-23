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

export interface ConsumerEntry {
  packageName: string;
  classLoaderId: number;
  classLoaderClassName: string | null;
  retainedSize: number;
  shallowSize: number;
  classCount: number;
  instanceCount: number;
}

export interface ComponentEntry {
  packageName: string;
  retainedSize: number;
  shallowSize: number;
  classCount: number;
  instanceCount: number;
}

export default interface ConsumerReport {
  totalHeapSize: number;
  topConsumers: ConsumerEntry[];
  componentReport: ComponentEntry[];
}
