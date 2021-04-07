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
package pbouda.jeffrey.view.input;

import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.AjaxBehaviorEvent;
import javax.inject.Named;

import org.primefaces.component.colorpicker.ColorPicker;

@Named
@RequestScoped
public class ColorView {

    private String colorInline;

    private String colorPopup;

    public String getColorInline() {
        return colorInline;
    }

    public void setColorInline(String colorInline) {
        this.colorInline = colorInline;
    }

    public String getColorPopup() {
        return colorPopup;
    }

    public void setColorPopup(String colorPopup) {
        this.colorPopup = colorPopup;
    }

    public void onColorChange(AjaxBehaviorEvent e) {
        ColorPicker picker = (ColorPicker) e.getComponent();
        FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_INFO, "Color changed: " + picker.getValue(), null);
        FacesContext.getCurrentInstance().addMessage(null, msg);
    }
    
    public void onPopupClosed(AjaxBehaviorEvent e) {
        ColorPicker picker = (ColorPicker) e.getComponent();
        FacesMessage msg = new FacesMessage(FacesMessage.SEVERITY_INFO, "Popup closed: " + picker.getValue(), null);
        FacesContext.getCurrentInstance().addMessage(null, msg);
    }
}
