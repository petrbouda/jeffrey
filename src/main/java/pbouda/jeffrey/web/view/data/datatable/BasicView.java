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
package pbouda.jeffrey.web.view.data.datatable;

import javax.faces.view.ViewScoped;
import pbouda.jeffrey.web.domain.Car;
import pbouda.jeffrey.web.service.CarService;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.List;

@Named("dtBasicView")
@ViewScoped
public class BasicView implements Serializable {
    
    private List<Car> cars;
    
    @Inject
    private CarService service;

    @PostConstruct
    public void init() {
        cars = service.createCars(10);
    }
    
    public List<Car> getCars() {
        return cars;
    }

    public void setService(CarService service) {
        this.service = service;
    }
}
