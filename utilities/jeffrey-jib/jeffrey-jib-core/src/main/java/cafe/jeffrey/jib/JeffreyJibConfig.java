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

package cafe.jeffrey.jib;

/**
 * User-facing configuration for the Jeffrey JIB plugin extension. Populated by JIB's Maven /
 * Gradle plugin DSL via bean-style setters (JIB's reflection-based config binding requires
 * JavaBean accessors, which is why this is a plain class rather than a record).
 *
 * <p>All string fields are optional. Null means "do not set an image-level ENV default for
 * this key" — the wrapper script's hardcoded fallback applies at container start, or the
 * operator provides the value via a pod-level env var.
 */
public class JeffreyJibConfig {

    private boolean enabled = true;
    private String jeffreyHome;
    private String baseConfig;
    private String overrideConfig;
    private String cliPath;
    private String argFile;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getJeffreyHome() {
        return jeffreyHome;
    }

    public void setJeffreyHome(String jeffreyHome) {
        this.jeffreyHome = jeffreyHome;
    }

    public String getBaseConfig() {
        return baseConfig;
    }

    public void setBaseConfig(String baseConfig) {
        this.baseConfig = baseConfig;
    }

    public String getOverrideConfig() {
        return overrideConfig;
    }

    public void setOverrideConfig(String overrideConfig) {
        this.overrideConfig = overrideConfig;
    }

    public String getCliPath() {
        return cliPath;
    }

    public void setCliPath(String cliPath) {
        this.cliPath = cliPath;
    }

    public String getArgFile() {
        return argFile;
    }

    public void setArgFile(String argFile) {
        this.argFile = argFile;
    }
}
