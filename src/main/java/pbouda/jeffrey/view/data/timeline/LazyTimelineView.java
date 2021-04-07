/*
 * Copyright 2009-2019 PrimeTek.
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

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.PrimitiveIterator;
import java.util.Random;

import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Named;

import org.primefaces.component.timeline.TimelineUpdater;
import org.primefaces.event.timeline.TimelineLazyLoadEvent;
import org.primefaces.model.timeline.TimelineEvent;
import org.primefaces.model.timeline.TimelineModel;

@Named
@ViewScoped
public class LazyTimelineView implements Serializable {

    private TimelineModel<String, ?> model;

    private float preloadFactor = 0;
    private long zoomMax;

    @PostConstruct
    protected void initialize() {
        // create empty model
        model = new TimelineModel<>();

        // about five months in milliseconds for zoomMax
        // this can help to avoid a long loading of events when zooming out to wide time ranges
        zoomMax = 1000L * 60 * 60 * 24 * 31 * 5;
    }

    public TimelineModel<String, ?> getModel() {
        return model;
    }

    public void onLazyLoad(TimelineLazyLoadEvent e) {
        try {
            // simulate time-consuming loading before adding new events
            Thread.sleep((long) (1000 * Math.random() + 100));
        } catch (InterruptedException ex) {
            // ignore
        }

        TimelineUpdater timelineUpdater = TimelineUpdater.getCurrentInstance(":form:timeline");

        LocalDateTime startDate = e.getStartDateFirst(); // alias getStartDate() can be used too
        LocalDateTime endDate = e.getEndDateFirst(); // alias getEndDate() can be used too

        // fetch events for the first time range
        generateRandomEvents(startDate, endDate, timelineUpdater);

        if (e.hasTwoRanges()) {
            // zooming out ==> fetch events for the second time range
            generateRandomEvents(e.getStartDateSecond(), e.getEndDateSecond(), timelineUpdater);
        }
    }

    private void generateRandomEvents(LocalDateTime startDate, LocalDateTime endDate, TimelineUpdater timelineUpdater) {
        LocalDateTime curDate = startDate;
        Random rnd = new Random();
        PrimitiveIterator.OfInt randomInts = rnd.ints(1, 99999).iterator();

        while (curDate.isBefore(endDate)) {
            // create events in the given time range
            if (rnd.nextBoolean()) {
                // event with only one date
                model.add(TimelineEvent.<String>builder()
                        .data("Event " + randomInts.nextInt())
                        .startDate(curDate)
                        .build(), timelineUpdater);
            } else {
                // event with start and end dates
                model.add(TimelineEvent.<String>builder()
                        .data("Event " + randomInts.nextInt())
                        .startDate(curDate)
                        .endDate(curDate.plusHours(18))
                        .build(),
                        timelineUpdater);
            }

            curDate = curDate.plusHours(24);
        }
    }

    public void clearTimeline() {
        // clear Timeline, so that it can be loaded again with a new preload factor
        model.clear();
    }

    public void setPreloadFactor(float preloadFactor) {
        this.preloadFactor = preloadFactor;
    }

    public float getPreloadFactor() {
        return preloadFactor;
    }

    public long getZoomMax() {
        return zoomMax;
    }
}
