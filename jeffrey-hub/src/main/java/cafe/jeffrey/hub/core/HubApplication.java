/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package cafe.jeffrey.hub.core;

import cafe.jeffrey.shared.common.JeffreyVersion;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * The hub: the scheduler's jobs on the recording volume and a JSON API under {@code /api/v1}.
 * It serves no pages. Its Docker image is a GraalVM native image ({@code build-hub-native});
 * {@code build-hub} keeps the plain jar.
 */
@SpringBootApplication
@ImportRuntimeHints(HubRuntimeHints.class)
public class HubApplication {

    private static final String VERSION_FLAG = "--version";

    static void main(String[] args) {
        if (args.length > 0 && VERSION_FLAG.equals(args[0])) {
            JeffreyVersion.print();
            return;
        }
        runApplication(args);
    }

    private static void runApplication(String[] args) {
        SpringApplication app = new SpringApplication(HubApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }
}
