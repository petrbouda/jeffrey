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
package cafe.jeffrey.profile.heapdump.view;

/**
 * JDK instance-field names that the heap-dump analyzers match against. Kept
 * in one place so the same literal isn't scattered across analyzers that all
 * need to recognise {@code String.value}, {@code String.coder}, or
 * {@code Thread.name}.
 */
public final class JdkFieldNames {

    public static final String STRING_VALUE = "value";
    public static final String STRING_CODER = "coder";
    public static final String THREAD_NAME = "name";

    private JdkFieldNames() {
    }
}
