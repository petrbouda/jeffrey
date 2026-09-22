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


package cafe.jeffrey.shared.common.model.repository;

import cafe.jeffrey.shared.common.config.ConfigScope;

/**
 * One hub-published file the provisioner merged into a session's configuration, as recorded in
 * {@code .session-info.json}. The digest is the SHA-256 of the file's bytes, lower-case hex, which
 * the hub computes identically over the file it renders — so a session can be matched to the exact
 * version it ran with without a version number travelling through the file.
 */
public record AppliedConfigLayer(ConfigScope scope, String digest) {

    public AppliedConfigLayer {
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        if (digest == null || digest.isBlank()) {
            throw new IllegalArgumentException("digest must not be blank");
        }
    }
}
