/*
 * Copyright 2009-2016 PrimeTek.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pbouda.jeffrey.view.data.timeline;

import org.primefaces.component.timeline.TimelineUpdater;
import org.primefaces.event.timeline.TimelineSelectEvent;
import org.primefaces.model.timeline.TimelineEvent;
import org.primefaces.model.timeline.TimelineModel;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Named("linkedTimelinesView")
@ViewScoped
public class LinkedTimelinesView implements Serializable {

    private TimelineModel<Task, ?> modelFirst;  // model of the first timeline
    private TimelineModel<String, ?> modelSecond; // model of the second timeline
    private boolean aSelected;         // flag if the project A is selected (for test of select() call on the 2. model)  

    @PostConstruct
    public void init() {
        createFirstTimeline();
        createSecondTimeline();
    }

    private void createFirstTimeline() {
        modelFirst = new TimelineModel<>();

        modelFirst.add(TimelineEvent.<Task>builder().data(new Task("Mail from boss", "images/timeline/mail.png", false)).startDate(LocalDateTime.of(2015, 8, 22, 17, 30)).build());
        modelFirst.add(TimelineEvent.<Task>builder().data(new Task("Call back my boss", "images/timeline/callback.png", false)).startDate(LocalDateTime.of(2015, 8, 23, 23, 0)).build());
        modelFirst.add(TimelineEvent.<Task>builder().data(new Task("Travel to Spain", "images/timeline/location.png", false)).startDate(LocalDateTime.of(2015, 8, 24, 21, 45)).build());
        modelFirst.add(TimelineEvent.<Task>builder().data(new Task("Do homework", "images/timeline/homework.png", true)).startDate(LocalDate.of(2015, 8, 26)).endDate(LocalDate.of(2015, 9, 2)).build());
        modelFirst.add(TimelineEvent.<Task>builder().data(new Task("Create memo", "images/timeline/memo.png", false)).startDate(LocalDate.of(2015, 8, 28)).build());
        modelFirst.add(TimelineEvent.<Task>builder().data(new Task("Create report", "images/timeline/report.png", true)).startDate(LocalDate.of(2015, 8, 31)).endDate(LocalDate.of(2015,9, 3)).build());
    }

    private void createSecondTimeline() {
        modelSecond = new TimelineModel<>();

        modelSecond.add(TimelineEvent.<String>builder().data("Project A").startDate(LocalDate.of(2015, 8, 23)).endDate(LocalDate.of(2015, 8, 30)).build());
        modelSecond.add(TimelineEvent.<String>builder().data("Project B").startDate(LocalDate.of(2015, 8, 27)).endDate(LocalDate.of(2015, 8, 31)).build());
    }

    public void onSelect(TimelineSelectEvent<?> e) {
        // get a thread-safe TimelineUpdater instance for the second timeline  
        TimelineUpdater timelineUpdater = TimelineUpdater.getCurrentInstance(":form:timelineSecond");

        if (aSelected) {
            // select project B visually (index in the event's list is 1)  
            timelineUpdater.select("projectB");
        } else {
            // select project A visually (index in the event's list is 0)  
            timelineUpdater.select("projectA");
        }

        aSelected = !aSelected;

        FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_INFO,
                "Selected project: " + (aSelected ? "A" : "B"), null);
        FacesContext.getCurrentInstance().addMessage(null, msg);
    }

    public TimelineModel<Task, ?> getModelFirst() {
        return modelFirst;
    }

    public TimelineModel<String, ?> getModelSecond() {
        return modelSecond;
    }

    public class Task implements Serializable {

        private final String title;
        private final String imagePath;
        private final boolean period;

        public Task(String title, String imagePath, boolean period) {
            this.title = title;
            this.imagePath = imagePath;
            this.period = period;
        }

        public String getTitle() {
            return title;
        }

        public String getImagePath() {
            return imagePath;
        }

        public boolean isPeriod() {
            return period;
        }
    }
}
