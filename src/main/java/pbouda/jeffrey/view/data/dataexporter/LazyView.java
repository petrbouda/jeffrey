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
package pbouda.jeffrey.view.data.dataexporter;

import org.primefaces.model.LazyDataModel;
import pbouda.jeffrey.domain.Car;
import pbouda.jeffrey.service.CarService;
import pbouda.jeffrey.view.data.datatable.LazyCarDataModel;

import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named("deLazyView")
@ViewScoped
public class LazyView implements Serializable {
    
    private LazyDataModel<Car> lazyModel;
    
    private List<Car> filteredCars;

    private String sortMode = "single";

    @Inject
    private CarService service;
    
    @PostConstruct
    public void init() {
        lazyModel = new LazyCarDataModel(service.createCars(200));
    }

    public LazyDataModel<Car> getLazyModel() {
        return lazyModel;
    }

    public List<Car> getFilteredCars() {
        return filteredCars;
    }

    public void setFilteredCars(List<Car> filteredCars) {
        this.filteredCars = filteredCars;
    }

    public void setService(CarService service) {
        this.service = service;
    }

    public String getSortMode() {
        return sortMode;
    }

    public void setSortMode(String sortMode) {
        this.sortMode = sortMode;
    }
}
