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

import pbouda.jeffrey.domain.Order;
import org.primefaces.event.timeline.*;
import org.primefaces.model.timeline.TimelineEvent;
import org.primefaces.model.timeline.TimelineModel;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.primefaces.PrimeFaces;
import org.primefaces.component.timeline.TimelineUpdater;
import org.primefaces.model.timeline.TimelineGroup;

@Named("groupingTimelineView")
@ViewScoped
public class GroupingTimelineView implements Serializable {
  
    private TimelineModel<Order, Truck> model;
    private TimelineEvent<Order> event; // current changed event
    private List<TimelineEvent<Order>> overlappedOrders; // all overlapped orders (events) to the changed order (event)
    private List<TimelineEvent<Order>> ordersToMerge; // selected orders (events) in the dialog which should be merged

    @PostConstruct
    protected void initialize() {
        // create timeline model
        model = new TimelineModel<>();

        // create groups
        TimelineGroup<Truck> group1 = new TimelineGroup<>("id1", new Truck("10"));
        TimelineGroup<Truck> group2 = new TimelineGroup<>("id2", new Truck("11"));
        TimelineGroup<Truck> group3 = new TimelineGroup<>("id3", new Truck("12"));
        TimelineGroup<Truck> group4 = new TimelineGroup<>("id4", new Truck("13"));
        TimelineGroup<Truck> group5 = new TimelineGroup<>("id5", new Truck("14"));
        TimelineGroup<Truck> group6 = new TimelineGroup<>("id6", new Truck("15"));

        // add groups to the model
        model.addGroup(group1);
        model.addGroup(group2);
        model.addGroup(group3);
        model.addGroup(group4);
        model.addGroup(group5);
        model.addGroup(group6);

        int orderNumber = 1;

        // iterate over groups
        for (int j = 1; j <= 6; j++) {
            LocalDateTime referenceDate = LocalDateTime.of(2015, Month.DECEMBER, 14, 8, 0);
            // iterate over events in the same group
            for (int i = 0; i < 6; i++) {
                LocalDateTime startDate = referenceDate.plusHours(3 * (Math.random() < 0.2 ? 1 : 0));

                LocalDateTime endDate = startDate.plusHours(2 + (int) Math.floor(Math.random() * 4));

                String imagePath = null;
                if (Math.random() < 0.25) {
                    imagePath = "images/timeline/box.png";
                }

                Order order = new Order(orderNumber, imagePath);
                TimelineEvent<Order> event = new TimelineEvent<>();
                event.setData(order);
                event.setStartDate(startDate);
                event.setEndDate(endDate);
                event.setEditableTime(true);
                event.setTitle("id" + j);
                model.add(event);

                orderNumber++;
                referenceDate = endDate;
            }
        }
    }

    public TimelineModel<Order, Truck> getModel() {
        return model;
    }

    public void onChange(TimelineModificationEvent<Order> e) {
        // get changed event and update the model
        event = e.getTimelineEvent();
        model.update(event);

        // get overlapped events of the same group as for the changed event
        Set<TimelineEvent<Order>> overlappedEvents = model.getOverlappedEvents(event);

        if (overlappedEvents == null) {
            // nothing to merge
            return;
        }

        // list of orders which can be merged in the dialog
        overlappedOrders = new ArrayList<>(overlappedEvents);

        // no pre-selection
        ordersToMerge = null;

        // update the dialog's content and show the dialog
        PrimeFaces primefaces = PrimeFaces.current();
        primefaces.ajax().update("form:overlappedOrdersInner");
        primefaces.executeScript("PF('overlapEventsWdgt').show()");
    }

    public void onDelete(TimelineModificationEvent<Order> e) {
        // keep the model up-to-date
        model.delete(e.getTimelineEvent());
    }

    public void merge() {
        // merge orders and update UI if the user selected some orders to be merged
        if (ordersToMerge != null && !ordersToMerge.isEmpty()) {
            model.merge(event, ordersToMerge, TimelineUpdater.getCurrentInstance(":form:timeline"));
        } else {
            FacesMessage msg =
                    new FacesMessage(FacesMessage.SEVERITY_INFO, "Nothing to merge, please choose orders to be merged", null);
            FacesContext.getCurrentInstance().addMessage(null, msg);
        }

        overlappedOrders = null;
        ordersToMerge = null;
    }

    public int getSelectedOrder() {
        if (event == null) {
            return 0;
        }

        return event.getData().getNumber();
    }

    public List<TimelineEvent<Order>> getOverlappedOrders() {
        return overlappedOrders;
    }

    public List<TimelineEvent<Order>> getOrdersToMerge() {
        return ordersToMerge;
    }

    public void setOrdersToMerge(List<TimelineEvent<Order>> ordersToMerge) {
        this.ordersToMerge = ordersToMerge;
    }

    public static class Truck implements java.io.Serializable {
        private final String code;

        public Truck(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}  
