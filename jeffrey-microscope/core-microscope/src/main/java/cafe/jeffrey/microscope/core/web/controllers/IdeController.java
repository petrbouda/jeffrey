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

import cafe.jeffrey.microscope.core.manager.ide.IdeManager;
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenRequest;
import cafe.jeffrey.microscope.core.manager.ide.IdeOpenResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/ide")
public class IdeController {

    private final IdeManager ideManager;

    public IdeController(IdeManager ideManager) {
        this.ideManager = ideManager;
    }

    @PostMapping("/open")
    public IdeOpenResponse open(@RequestBody IdeOpenRequest request) {
        IdeOpenResult result = ideManager.open(request);
        return new IdeOpenResponse(result.success(), result.message());
    }

    public record IdeOpenResponse(boolean success, String message) {
    }
}
