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
package pbouda.jeffrey.web.view.button;

import org.primefaces.model.menu.DefaultMenuItem;
import org.primefaces.model.menu.DefaultMenuModel;
import org.primefaces.model.menu.DefaultSubMenu;
import org.primefaces.model.menu.MenuModel;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Named;

@Named
@RequestScoped
public class ButtonView {

    private MenuModel model;

    @PostConstruct
    public void init() {
        model = new DefaultMenuModel();

        //First submenu
        DefaultMenuItem item = DefaultMenuItem.builder()
                .value("External")
                .url("http://www.primefaces.org")
                .icon("pi pi-home")
                .build();


        DefaultSubMenu firstSubmenu = DefaultSubMenu.builder()
                .label("Dynamic Submenu")
                .addElement(item)
                .build();

        model.getElements().add(firstSubmenu);

        //Second submenu
        item = DefaultMenuItem.builder()
                .value("Save")
                .icon("pi pi-save")
                .function((i) -> save())
                .update("messages")
                .build();

        DefaultSubMenu secondSubmenu = DefaultSubMenu.builder()
                .label("Dynamic Actions")
                .addElement(item)
                .build();

        item = DefaultMenuItem.builder()
                .value("Delete")
                .icon("pi pi-times")
                .command("#{buttonView.delete}")
                .ajax(false)
                .build();
        secondSubmenu.getElements().add(item);

        model.getElements().add(secondSubmenu);
    }

    public MenuModel getModel() {
        return model;
    }

    public String save() {
        addMessage("Data saved");
        return null;
    }

    public void update() {
        addMessage("Data updated");
    }

    public void delete() {
        addMessage("Data deleted");
    }

    public void buttonAction() {
        addMessage("Welcome to PrimeFaces!!");
    }

    public void addMessage(String summary) {
        FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO, summary, null);
        FacesContext.getCurrentInstance().addMessage(null, message);
    }
}
