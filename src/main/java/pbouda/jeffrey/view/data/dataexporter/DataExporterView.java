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

import java.io.Serializable;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.primefaces.component.datatable.DataTable;
import org.primefaces.component.export.Exporter;
import pbouda.jeffrey.domain.Car;
import pbouda.jeffrey.service.CarService;

@Named
@RequestScoped
public class DataExporterView implements Serializable {
    
    private List<Car> cars;
    
    private Exporter<DataTable> textExporter;
        
    @Inject
    private CarService service;
    
    @PostConstruct
    public void init() {
        cars = service.createCars(100);
        textExporter = new TextExporter();
    }

    public List<Car> getCars() {
        return cars;
    }

    public void setService(CarService service) {
        this.service = service;
    }

    public Exporter<DataTable> getTextExporter() {
        return textExporter;
    }

    public void setTextExporter(Exporter<DataTable> textExporter) {
        this.textExporter = textExporter;
    }
    
    
}
