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

package cafe.jeffrey.provider.profile.jdbc;

/**
 * The key a text dictionary row is stored under, computed inline from the text itself so a
 * derivation can write a reference without joining back to find it.
 * <p>
 * The key is a non-negative {@code BIGINT} folded out of the text's MD5. It used to be DuckDB's
 * {@code hash()}, which is no fit for this: on {@code VARCHAR} it folds the bytes eight at a time
 * with a xor and a multiply, and a multiply only carries bits upwards, so two texts that differ
 * only in the top byte of two of their eight-byte words collide one time in 256. JSON payloads
 * that differ only in a couple of digits are exactly that shape, and a collision fails the
 * dictionary's primary key and with it the whole profile initialization. MD5 costs more per row,
 * and buys a key whose collisions stay at the birthday bound of 63 bits.
 * <p>
 * Every statement that writes a dictionary row and every statement that writes a reference into it
 * has to render its key through here: the two only meet if they agree byte for byte.
 */
final class TextKeys {

    private static final String KEY_TEMPLATE =
            "CAST(mod(md5_number(%s), CAST(9223372036854775807 AS UHUGEINT)) AS BIGINT)";

    private TextKeys() {
    }

    /**
     * @param textExpression a SQL expression of type {@code VARCHAR}
     * @return the expression of its key; {@code NULL} where the text is {@code NULL}
     */
    static String of(String textExpression) {
        return KEY_TEMPLATE.formatted(textExpression);
    }
}
