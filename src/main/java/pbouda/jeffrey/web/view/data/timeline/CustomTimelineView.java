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

import org.primefaces.model.timeline.TimelineEvent;
import org.primefaces.model.timeline.TimelineModel;

import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;
import java.time.LocalDateTime;

@Named("customTimelineView")
@ViewScoped
public class CustomTimelineView implements Serializable {  
  
    private TimelineModel<String, ?> model;
    private LocalDateTime start;
    private LocalDateTime end;
  
    @PostConstruct  
    public void init() {  
        // set initial start / end dates for the axis of the timeline  
        start = LocalDateTime.now().minusHours(4);
        end = LocalDateTime.now().plusHours(8);

        // groups  
        String[] NAMES = new String[] {"User 1", "User 2", "User 3", "User 4", "User 5", "User 6"};  
  
        // create timeline model  
        model = new TimelineModel<>();
  
        for (String name : NAMES) {
            LocalDateTime end = LocalDateTime.now().minusHours(12).withMinute(0).withSecond(0).withNano(0);

            for (int i = 0; i < 5; i++) {
                LocalDateTime start = end.plusHours(Math.round(Math.random() *5));
                end = start.plusHours(4 + Math.round(Math.random() *5));

                long r = Math.round(Math.random() * 2);  
                String availability = (r == 0 ? "Unavailable" : (r == 1 ? "Available" : "Maybe"));  
  
                // create an event with content, start / end dates, editable flag, group name and custom style class
                TimelineEvent event = TimelineEvent.builder()
                        .data(availability)
                        .startDate(start)
                        .endDate(end)
                        .editable(true)
                        .group(name)
                        .styleClass(availability.toLowerCase())
                        .build();

                model.add(event);
            }  
        }  
    }  
  
    public TimelineModel<String, ?> getModel() {
        return model;  
    }  
  
    public LocalDateTime getStart() {
        return start;  
    }  
  
    public LocalDateTime getEnd() {
        return end;  
    }  
}  
