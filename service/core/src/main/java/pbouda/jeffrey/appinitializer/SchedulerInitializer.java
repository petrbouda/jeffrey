/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.appinitializer;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import pbouda.jeffrey.manager.ProjectsManager;
import pbouda.jeffrey.project.repository.RemoteRepositoryStorage;
import pbouda.jeffrey.scheduler.PeriodicalScheduler;
import pbouda.jeffrey.scheduler.Scheduler;
import pbouda.jeffrey.scheduler.task.RecordingGeneratorJob;
import pbouda.jeffrey.scheduler.task.RepositoryCleanerJob;

import java.time.Duration;
import java.util.List;

public class SchedulerInitializer implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableApplicationContext context = event.getApplicationContext();
        ProjectsManager projectsManager = context.getBean(ProjectsManager.class);
        RemoteRepositoryStorage.Factory remoteRepositoryManagerFactory =
                context.getBean(RemoteRepositoryStorage.Factory.class);

        List<Runnable> tasks = List.of(
                new RepositoryCleanerJob(projectsManager, remoteRepositoryManagerFactory),
                new RecordingGeneratorJob(projectsManager, remoteRepositoryManagerFactory));

        Scheduler scheduler = new PeriodicalScheduler(Duration.ofMinutes(1), tasks);
        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdown));
        scheduler.start();
    }
}
