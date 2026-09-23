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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.custom.JdbcPoolManager;
import cafe.jeffrey.profile.manager.custom.model.jdbc.pool.JdbcPoolData;
import cafe.jeffrey.microscope.model.Type;
import cafe.jeffrey.timeseries.SingleSerie;

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
