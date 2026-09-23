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
package cafe.jeffrey.profile.heapdump.oql.executor;

import cafe.jeffrey.profile.heapdump.view.HeapView;
import cafe.jeffrey.profile.heapdump.view.InstanceRow;
import cafe.jeffrey.profile.heapdump.view.JavaClassRow;

/**
 * Per-iteration context passed to {@link ExprEvaluator}. {@code instance} is
 * the candidate row being evaluated; {@code clazz} is its resolved class
 * descriptor; {@code bindingName} is the FROM-clause alias the user gave (or
 * {@code null} when no alias).
 */
public record Row(HeapView view, InstanceRow instance, JavaClassRow clazz, String bindingName) {

    public Row {
        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (instance == null) {
            throw new IllegalArgumentException("instance must not be null");
        }
        if (clazz == null) {
            throw new IllegalArgumentException("clazz must not be null");
        }
    }
}
