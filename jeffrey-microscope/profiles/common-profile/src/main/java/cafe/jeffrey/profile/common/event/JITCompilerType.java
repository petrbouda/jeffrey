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

package cafe.jeffrey.profile.common.event;

import com.fasterxml.jackson.annotation.JsonValue;

public enum JITCompilerType {
    C1("c1"), C2("c2"), JVMCI("jvmci");

    private final String name;

    JITCompilerType(String name) {
        this.name = name;
    }

    @JsonValue
    public String getName() {
        return name;
    }
}
