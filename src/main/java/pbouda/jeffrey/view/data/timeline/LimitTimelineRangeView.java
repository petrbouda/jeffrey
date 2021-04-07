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

import org.primefaces.model.timeline.TimelineEvent;
import org.primefaces.model.timeline.TimelineModel;

import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Named;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Named("limitTimelineRangeView")
@ViewScoped
public class LimitTimelineRangeView implements Serializable {

	private TimelineModel<String, ?> model;

	private LocalDateTime min;
	private LocalDateTime max;
	private long zoomMin;
	private long zoomMax;

	@PostConstruct
	public void init() {
		model = new TimelineModel<>();

		model.add(TimelineEvent.<String>builder().data("First").startDate(LocalDate.of(2015, 5, 25)).build());
		model.add(TimelineEvent.<String>builder().data("Last").startDate(LocalDate.of(2015, 5, 26)).build());

		// lower limit of visible range
		min = LocalDate.of(2015, 1,1).atStartOfDay();

		// upper limit of visible range
		max = LocalDate.of(2015, 12, 31).atStartOfDay();

		// one day in milliseconds for zoomMin
		zoomMin = 1000L * 60 * 60 * 24;

		// about three months in milliseconds for zoomMax
		zoomMax = 1000L * 60 * 60 * 24 * 31 * 3;
	}

	public TimelineModel<String, ?> getModel() {
		return model;
	}

	public LocalDateTime getMin() {
		return min;
	}

	public LocalDateTime getMax() {
		return max;
	}

	public long getZoomMin() {
		return zoomMin;
	}

	public long getZoomMax() {
		return zoomMax;
	}
    
}
