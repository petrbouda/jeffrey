/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.hub.core.scheduler.history;

/**
 * Discards everything. Used by job contexts created outside the scheduler funnel
 * so jobs can report unconditionally without null-checks.
 */
public final class NoopJobExecutionReport implements JobExecutionReport {

    public static final NoopJobExecutionReport INSTANCE = new NoopJobExecutionReport();

    private NoopJobExecutionReport() {
    }

    @Override
    public void summary(String summary) {
    }

    @Override
    public void item(String item) {
    }

    @Override
    public void failure(String item) {
    }
}
