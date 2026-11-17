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

package cafe.jeffrey.flamegraph.export;

/**
 * What shape the Markdown export takes.
 */
public enum AiExportView {

    /** The call tree, pruned at the configured threshold, every kept frame once. */
    TREE,

    /**
     * The methods with the most self time or weight, summed across every path they were called from,
     * and the few complete stacks that hold the most of it. A first look that fits in a few kilobytes
     * whatever the size of the tree.
     */
    SUMMARY
}
