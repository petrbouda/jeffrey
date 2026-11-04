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
 * The id a text is stored under in a dictionary table — a span's payload, an attribute's value, a
 * notification's message — computed in SQL from the text itself, so the row that references it can
 * compute the same id inline instead of joining back to find it.
 *
 * <p>The id is the text's MD5, folded into a non-negative {@code BIGINT}. DuckDB's own {@code hash()}
 * is not fit for this: it is built for hash tables, where a collision costs a comparison, and it gives
 * two near-identical texts the same 64 bits — two {@code DELETE /persons/{id}} exchanges differing only
 * in their remote port and path id did, on a real recording of a few thousand requests, and the
 * dictionary's primary key failed the whole trace derivation.
 */
final class TextIds {

    /** Folds the digest into the non-negative half of a {@code BIGINT}. */
    private static final String ID_OF = "CAST(mod(md5_number(%s), CAST(9223372036854775807 AS UHUGEINT)) AS BIGINT)";

    private TextIds() {
    }

    /** The SQL expression for the id of the text {@code textExpression} evaluates to; NULL for a NULL text. */
    static String of(String textExpression) {
        return ID_OF.formatted(textExpression);
    }
}
