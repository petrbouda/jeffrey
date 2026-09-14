/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.shared.common.model.repository.matcher;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Recognises a HotSpot fatal-error log under either spelling it arrives in. {@code hs-jvm-err.log} is
 * Jeffrey's own: the provisioner sets it through {@code -XX:ErrorFile} so a session holds one crash
 * file under one stable name, the way {@code .jvm-log} keeps unified logging apart from application
 * logs. {@code hs_err_pid<pid>.log} is what a JVM Jeffrey did not configure writes on its own —
 * the {@code ErrorFile} default, with the timestamp {@code %t} expands to allowed after the pid.
 *
 * <p>Both spellings end in {@code .log}, so the type using this matcher must stay declared before
 * {@code APP_LOG}: read the other way round, a crash is filed as an application log and the hub's
 * crash detection never sees it. Whole names only — a copy renamed {@code app-hs-jvm-err.log} or a
 * compressed {@code hs-jvm-err.log.gz} is deliberately not claimed.
 */
public class HsJvmErrorLogFileMatcher implements Predicate<String> {

    private static final Pattern JEFFREY_NAME = Pattern.compile("^hs-jvm-err\\.log$");
    private static final Pattern JVM_DEFAULT_NAME = Pattern.compile("^hs_err_pid[0-9]+(_[0-9_\\-]+)?\\.log$");

    @Override
    public boolean test(String filename) {
        if (filename == null) {
            return false;
        }
        return JEFFREY_NAME.matcher(filename).matches() || JVM_DEFAULT_NAME.matcher(filename).matches();
    }
}
