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

/**
 * One row of the "Largest String Instances" table — a single String object
 * ranked by sharing-aware GC-retained size. content is always a truncated
 * preview, even when the backing string exceeded the indexer's cap (the
 * backend re-decodes from the heap dump in that case).
 */
export default interface StringInstanceEntry {
  content: string;
  instanceId: number;
  arrayShallowSize: number;
  arrayRefCount: number;
  retainedSize: number;
}
