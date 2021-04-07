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
package pbouda.jeffrey.view.data.datatable;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import pbouda.jeffrey.domain.Car;
import pbouda.jeffrey.service.CarService;

@Named("dtFilterView")
@ViewScoped
public class FilterView implements Serializable {

    private List<Car> cars1;
    private List<Car> cars2;

    private List<Car> filteredCars1;
    private List<Car> filteredCars2;

    @Inject
    private CarService service;

    @PostConstruct
    public void init() {
        cars1 = service.createCars(10);
        cars2 = service.createCars(10);
    }

    public boolean filterByPrice(Object value, Object filter, Locale locale) {
        String filterText = (filter == null) ? null : filter.toString().trim();
        if (filterText == null || filterText.equals("")) {
            return true;
        }

        if (value == null) {
            return false;
        }

        return ((Comparable) value).compareTo(getInteger(filterText)) > 0;
    }

    public boolean globalFilterFunction(Object value, Object filter, Locale locale) {
        String filterText = (filter == null) ? null : filter.toString().trim().toLowerCase();
        if (filterText == null || filterText.equals("")) {
            return true;
        }
        int filterInt = getInteger(filterText);

        Car car = (Car) value;
        return car.getId().toLowerCase().contains(filterText)
                || car.getBrand().toLowerCase().contains(filterText)
                || car.getColor().toLowerCase().contains(filterText)
                || (car.isSold() ? "sold" : "sale").contains(filterText)
                || car.getYear() < filterInt
                || car.getPrice() < filterInt;
    }

    private int getInteger(String string) {
        try {
            return Integer.valueOf(string);
        }
        catch (NumberFormatException ex) {
            return 0;
        }
    }

    public List<String> getBrands() {
        return service.getBrands();
    }

    public List<String> getColors() {
        return service.getColors();
    }

    public List<Car> getCars1() {
        return cars1;
    }

    public List<Car> getCars2() {
        return cars2;
    }

    public List<Car> getFilteredCars1() {
        return filteredCars1;
    }

    public void setFilteredCars1(List<Car> filteredCars1) {
        this.filteredCars1 = filteredCars1;
    }

    public List<Car> getFilteredCars2() {
        return filteredCars2;
    }

    public void setFilteredCars2(List<Car> filteredCars2) {
        this.filteredCars2 = filteredCars2;
    }

    public void setService(CarService service) {
        this.service = service;
    }
}
