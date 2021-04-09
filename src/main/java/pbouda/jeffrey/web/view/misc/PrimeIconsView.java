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
package pbouda.jeffrey.web.view.misc;

import org.primefaces.shaded.json.JSONArray;
import org.primefaces.shaded.json.JSONException;
import org.primefaces.shaded.json.JSONObject;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Named
@ApplicationScoped
public class PrimeIconsView implements Serializable {

    private List<Icon> icons;
    
    @PostConstruct
    public void init() {
        icons = new ArrayList<>();
        
        String url = "https://raw.githubusercontent.com/primefaces/primeicons/2.0.0/selection.json";
        try {
            JSONObject json = readJsonFromUrl(url);
            JSONArray iconsArray = json.getJSONArray("icons");
            for(int i = 0; i < iconsArray.length(); i++) {
                JSONObject properties = iconsArray.optJSONObject(i).getJSONObject("properties");
                icons.add(new Icon(properties.getString("name"), properties.getInt("code")));
            }
        } catch (IOException | JSONException ex) {
            Logger.getLogger(PrimeIconsView.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static String readAll(Reader rd) throws IOException {
        StringBuilder sb = new StringBuilder();
        int cp;
        while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
        }
        return sb.toString();
    }

    public JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
        InputStream is = new URL(url).openStream();
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
            String jsonText = readAll(rd);
            JSONObject json = new JSONObject(jsonText);
            return json;
        } finally {
            is.close();
        }
    }

    public List<Icon> getIcons() {
        return icons;
    }

    public void setIcons(List<Icon> icons) {
        this.icons = icons;
    }
    
    public class Icon {
        
        private String name;
        private int key;
        
        public Icon(String name, int key) {
            this.name = name;
            this.key = key;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getKey() {
            return key;
        }

        public void setKey(int key) {
            this.key = key;
        }
    }
}
