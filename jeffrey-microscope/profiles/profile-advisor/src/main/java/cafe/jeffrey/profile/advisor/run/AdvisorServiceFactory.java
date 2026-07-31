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

package cafe.jeffrey.profile.advisor.run;

import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.util.function.Function;

/**
 * Builds an {@link AdvisorService} bound to one profile. Everything per-profile — the profile database
 * and the project's settings — is resolved inside the factory call, which is also what makes a settings
 * edit apply to the very next run instead of the next restart.
 */
@FunctionalInterface
public interface AdvisorServiceFactory extends Function<ProfileInfo, AdvisorService> {
}
