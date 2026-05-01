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

package cafe.jeffrey.microscope.core.web.controllers.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import cafe.jeffrey.microscope.core.web.ProfileManagerResolver;
import cafe.jeffrey.profile.manager.custom.JdbcStatementManager;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcOverviewData;
import cafe.jeffrey.profile.manager.custom.model.jdbc.statement.JdbcSlowStatement;
import cafe.jeffrey.timeseries.SingleSerie;

import java.net.URLDecoder;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

@RestController
@RequestMapping("/api/internal/profiles/{profileId}/jdbc/statement/overview")
public class JdbcStatementController {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcStatementController.class);

    private final ProfileManagerResolver resolver;

    public JdbcStatementController(ProfileManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping
    public JdbcOverviewData overviewData(@PathVariable("profileId") String profileId) {
        LOG.debug("Fetching JDBC statement overview");
        return mgr(profileId).overviewData();
    }

    @GetMapping("/single")
    public JdbcOverviewData singleUriData(
            @PathVariable("profileId") String profileId,
            @RequestParam("group") String group) {
        LOG.debug("Fetching JDBC statement single group: group={}", group);
        return mgr(profileId).overviewData(URLDecoder.decode(group, UTF_8));
    }

    @GetMapping("/timeseries")
    public List<SingleSerie> specificTimeSeries(
            @PathVariable("profileId") String profileId,
            @RequestParam("group") String group,
            @RequestParam("statementName") String statementName) {
        LOG.debug("Fetching JDBC statement timeseries: group={} statementName={}", group, statementName);
        return mgr(profileId).timeseries(URLDecoder.decode(group, UTF_8), URLDecoder.decode(statementName, UTF_8));
    }

    @GetMapping("/slowest")
    public List<JdbcSlowStatement> specificSlowest(
            @PathVariable("profileId") String profileId,
            @RequestParam("group") String group,
            @RequestParam("statementName") String statementName) {
        LOG.debug("Fetching JDBC slowest statements: group={} statementName={}", group, statementName);
        return mgr(profileId).slowStatements(URLDecoder.decode(group, UTF_8), URLDecoder.decode(statementName, UTF_8));
    }

    private JdbcStatementManager mgr(String profileId) {
        return resolver.resolve(profileId).custom().jdbcStatementManager();
    }
}
