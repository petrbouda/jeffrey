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
package pbouda.jeffrey.web.view.dnd;

import org.primefaces.event.DragDropEvent;
import pbouda.jeffrey.web.domain.Car;
import pbouda.jeffrey.web.service.CarService;

import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Named("dndCarsView")
@ViewScoped
public class DNDCarsView implements Serializable {
 
    @Inject
    private CarService service;

    private List<Car> cars;
    
    private List<Car> droppedCars;
    
    private Car selectedCar;
    
    @PostConstruct
    public void init() {
        cars = service.createCars(9);
        droppedCars = new ArrayList<>();
    }
    
    public void onCarDrop(DragDropEvent<Car> ddEvent) {
        Car car = ddEvent.getData();
 
        droppedCars.add(car);
        cars.remove(car);
    }
    
    public void setService(CarService service) {
        this.service = service;
    }

    public List<Car> getCars() {
        return cars;
    }

    public List<Car> getDroppedCars() {
        return droppedCars;
    }    

    public Car getSelectedCar() {
        return selectedCar;
    }

    public void setSelectedCar(Car selectedCar) {
        this.selectedCar = selectedCar;
    }
}
