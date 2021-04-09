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
package pbouda.jeffrey.web.view.data.timeline;

import org.primefaces.component.timeline.TimelineUpdater;
import org.primefaces.event.timeline.TimelineDragDropEvent;
import org.primefaces.model.timeline.TimelineEvent;
import org.primefaces.model.timeline.TimelineModel;
import pbouda.jeffrey.web.domain.Event;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.AjaxBehaviorEvent;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Named("dndTimelineView")
@ViewScoped
public class DndTimelineView implements Serializable {

    private TimelineModel<Event, ?> model;

    private LocalDateTime start;
    private LocalDateTime end;
    
    private final List<Event> events = new ArrayList<>();

    @PostConstruct
    public void init() {
        start = LocalDateTime.now().minusHours(4);
        end = LocalDateTime.now().plusHours(8);

        model = new TimelineModel<>();

        for (int i = 1; i <= 10; i++) {
            events.add(new Event("Event " + i));
        }

    }

    public void onDrop(TimelineDragDropEvent<Event> e) {
        // get dragged model object (event class) if draggable item is within a data iteration component,
        // update event's start and end dates.
        Event dndEvent = e.getData();
        dndEvent.setStart(e.getStartDate());
        dndEvent.setEnd(e.getEndDate());

        // create a timeline event (not editable)
        TimelineEvent event = TimelineEvent.builder()
                .data(dndEvent)
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .editable(false)
                .group(e.getGroup())
                .build();

        // add a new event
        TimelineUpdater timelineUpdater = TimelineUpdater.getCurrentInstance("timeline");
        model.add(event, timelineUpdater);

        // remove from the list of all events
        events.remove(dndEvent);

        FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_INFO, "The " + dndEvent.getName() + " was added", null);
        FacesContext.getCurrentInstance().addMessage(null, msg);
    }

    public void onSwitchTimeZone(AjaxBehaviorEvent e) {
        model.clear();
    }

    public TimelineModel<Event, ?> getModel() {
        return model;
    }

    public List<Event> getEvents() {
        return events;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }
}
