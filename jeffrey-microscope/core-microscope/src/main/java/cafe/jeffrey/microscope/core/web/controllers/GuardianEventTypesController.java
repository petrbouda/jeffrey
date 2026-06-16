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

package cafe.jeffrey.microscope.core.web.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.controllers.GuardianEventTypeCatalog.EventTypeOption;

import java.util.List;

/**
 * Read-only catalog of stack-trace–carrying event types offered by the Guardian guard editor.
 * Lives at the (non-profile) platform path because guard configuration is shared across profiles.
 */
@RestController
@RequestMapping("/api/internal/guardian/event-types")
public class GuardianEventTypesController {

    @GetMapping
    public List<EventTypeOption> list() {
        return GuardianEventTypeCatalog.all();
    }
}
