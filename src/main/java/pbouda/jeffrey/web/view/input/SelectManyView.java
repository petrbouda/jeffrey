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
package pbouda.jeffrey.web.view.input;

import pbouda.jeffrey.web.domain.Theme;
import pbouda.jeffrey.web.service.ThemeService;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
@RequestScoped
public class SelectManyView {
    
    private List<String> selectedOptions;
    private List<String> selectedOptions2;
    private List<Theme> selectedThemes;
    private List<Theme> selectedThemes2;
    private List<Theme> themes;
    
    @Inject
    private ThemeService service;
    
    @PostConstruct
    public void init() {
        themes = service.getThemes();
    }

    public List<Theme> getThemes() {
        return themes;
    }

    public void setService(ThemeService service) {
        this.service = service;
    }

    public List<String> getSelectedOptions() {
        return selectedOptions;
    }

    public void setSelectedOptions(List<String> selectedOptions) {
        this.selectedOptions = selectedOptions;
    }

    public List<Theme> getSelectedThemes() {
        return selectedThemes;
    }

    public void setSelectedThemes(List<Theme> selectedThemes) {
        this.selectedThemes = selectedThemes;
    }

    public List<String> getSelectedOptions2() {
        return selectedOptions2;
    }

    public void setSelectedOptions2(List<String> selectedOptions2) {
        this.selectedOptions2 = selectedOptions2;
    }

    public List<Theme> getSelectedThemes2() {
        return selectedThemes2;
    }

    public void setSelectedThemes2(List<Theme> selectedThemes2) {
        this.selectedThemes2 = selectedThemes2;
    }
}
