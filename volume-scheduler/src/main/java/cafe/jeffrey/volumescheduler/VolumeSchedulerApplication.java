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

package cafe.jeffrey.volumescheduler;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * The volume scheduler: a small maintenance service on the shared recording volume. It runs the
 * retention and compression jobs over every project there and serves a JSON API under
 * {@code /api/v1}; it serves no pages and Microscope never talks to it. Its Docker image is a
 * GraalVM native image ({@code build-volume-scheduler-native}).
 */
@SpringBootApplication
@ImportRuntimeHints(NativeImageHints.class)
public class VolumeSchedulerApplication {

    static void main(String[] args) {
        SpringApplication app = new SpringApplication(VolumeSchedulerApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }
}
