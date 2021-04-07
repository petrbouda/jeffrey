/*
 * Copyright 2009-2014 PrimeTek.
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
package pbouda.jeffrey.view.data.gmap;

import org.primefaces.event.map.OverlaySelectEvent;
import org.primefaces.model.map.*;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Named;
import java.io.Serializable;

@Named
@RequestScoped
public class RectanglesView implements Serializable {
 
    private MapModel rectangleModel;
     
    @PostConstruct
    public void init() {
        rectangleModel = new DefaultMapModel();

        //Shared coordinates
        LatLng ne = new LatLng(36.879466, 30.667648);
        LatLng sw = new LatLng(36.885233, 30.702323);

        //Rectangle
        Rectangle rect = new Rectangle(new LatLngBounds(sw, ne));
        rect.setStrokeColor("#d93c3c");
        rect.setFillColor("#d93c3c");
        rect.setFillOpacity(0.5);
        rectangleModel.addOverlay(rect);
    }
 
    public MapModel getRectangleModel() {
        return rectangleModel;
    }
 
    public void onRectangleSelect(OverlaySelectEvent event) {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage(FacesMessage.SEVERITY_INFO, "Rectangle Selected", null));
    }
}
                    
