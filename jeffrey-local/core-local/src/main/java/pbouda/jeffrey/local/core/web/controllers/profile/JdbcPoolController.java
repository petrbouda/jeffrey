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

package pbouda.jeffrey.local.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pbouda.jeffrey.local.core.web.ProfileManagerResolver;
import pbouda.jeffrey.profile.manager.custom.JdbcPoolManager;
import pbouda.jeffrey.profile.manager.custom.model.jdbc.pool.JdbcPoolData;
import pbouda.jeffrey.shared.common.model.Type;
import pbouda.jeffrey.timeseries.SingleSerie;

import java.util.List;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/jdbc/pool")
public class JdbcPoolController {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcPoolController.class);

    public record TimeseriesRequest(String poolName, Type eventType) {
    }

    private final ProfileManagerResolver resolver;

    public JdbcPoolController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public List<JdbcPoolData> allPoolsData(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching JDBC pool data");
        return mgr(profileId).allPoolsData();
    }

    @PostMapping("/timeseries")
    public SingleSerie timeseries(
            @PathVariable("profileId") String profileId,
            @RequestBody TimeseriesRequest request) {
        LOG.debug("Fetching JDBC pool timeseries");
        return mgr(profileId).timeseries(request.poolName(), request.eventType());
    }

    private JdbcPoolManager mgr(String profileId) {
        return resolver.resolve(profileId).custom().jdbcPoolManager();
    }
}
