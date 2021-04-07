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
package pbouda.jeffrey.view.misc;

import pbouda.jeffrey.domain.Theme;
import pbouda.jeffrey.service.ThemeService;

import javax.annotation.PostConstruct;
import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.List;

@Named
@RequestScoped
public class ThemeSwitcherView {

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
}
