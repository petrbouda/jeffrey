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

package cafe.jeffrey.microscope.mcp.app;

import cafe.jeffrey.microscope.runtime.settings.SettingsApplicationListener;
import cafe.jeffrey.shared.common.JeffreyVersion;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microscope MCP — the headless Microscope.
 * <p>
 * Serves one thing: the external MCP endpoint at {@code POST /api/internal/mcp}, always on, over the
 * profiles already analysed in a Jeffrey home directory. It opens the same {@code ~/.jeffrey-microscope}
 * the full Microscope does, reads the same settings, and runs the same analysis engine — without the
 * web UI, the in-app AI providers or the hub client. Because DuckDB locks its files exclusively, the
 * two binaries take turns on a home directory rather than sharing it.
 * <p>
 * Scans {@code cafe.jeffrey.microscope.mcp}: the shared MCP server package (where the endpoint lives)
 * and this application's own sub-package.
 */
@SpringBootApplication(scanBasePackages = MicroscopeMcpApplication.MCP_PACKAGE)
public class MicroscopeMcpApplication {

    static final String MCP_PACKAGE = "cafe.jeffrey.microscope.mcp";

    private static final String VERSION_OPTION = "--version";

    static void main(String[] args) {
        if (args.length > 0 && VERSION_OPTION.equals(args[0])) {
            JeffreyVersion.print();
            return;
        }
        runApplication(args);
    }

    private static void runApplication(String[] args) {
        SpringApplication app = new SpringApplication(MicroscopeMcpApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.addListeners(new SettingsApplicationListener());
        app.addListeners(new McpStartedListener());
        app.run(args);
    }
}
