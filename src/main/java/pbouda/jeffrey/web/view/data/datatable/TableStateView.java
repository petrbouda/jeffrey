/*
 * Copyright 2009-2017 PrimeTek.
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
package pbouda.jeffrey.web.view.data.datatable;

import org.primefaces.PrimeFaces;
import pbouda.jeffrey.web.domain.Car;
import pbouda.jeffrey.web.service.CarService;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named("dtTableStateView")
@SessionScoped
public class TableStateView implements Serializable {
    
    private List<Car> cars;
    
    private List<Car> filteredCars;
    
    private Car selectedCar;
    
    @Inject
    private CarService service;

    @PostConstruct
    public void init() {
        cars = service.createCars(50);
    }
    
    public List<String> getBrands() {
        return service.getBrands();
    }
    
    public List<String> getColors() {
        return service.getColors();
    }
    
    public List<Car> getCars() {
        return cars;
    }

    public List<Car> getFilteredCars() {
        return filteredCars;
    }

    public Car getSelectedCar() {
        return selectedCar;
    }

    public void setSelectedCar(Car selectedCar) {
        this.selectedCar = selectedCar;
    }

    public void setFilteredCars(List<Car> filteredCars) {
        this.filteredCars = filteredCars;
    }

    public void setService(CarService service) {
        this.service = service;
    }

    public void clearTableState() {
        FacesContext context = FacesContext.getCurrentInstance();
        String viewId = context.getViewRoot().getViewId();
        PrimeFaces.current().multiViewState().clearAll(viewId, true, (clientId) -> {
            showMessage(clientId);
        });
    }

    private void showMessage(String clientId) {
        FacesContext.getCurrentInstance()
                .addMessage(null,
                        new FacesMessage(FacesMessage.SEVERITY_INFO, clientId + " multiview state has been cleared out", null));
    }
}
