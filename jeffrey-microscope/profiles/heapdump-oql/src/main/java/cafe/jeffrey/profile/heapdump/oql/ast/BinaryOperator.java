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
package cafe.jeffrey.profile.heapdump.oql.ast;

/**
 * Binary operators recognised by the parser. Both SQL-style ({@code AND},
 * {@code OR}, {@code =}, {@code !=}) and the {@code <>} inequality variant
 * map to the same enum constants.
 */
public enum BinaryOperator {
    AND,
    OR,
    EQ,
    NEQ,
    LT,
    LTE,
    GT,
    GTE,
    LIKE,
    IN,
    NOT_IN,
    IS_NULL,
    IS_NOT_NULL,
    ADD,
    SUB,
    MUL,
    DIV
}
