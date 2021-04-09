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

import javax.faces.view.ViewScoped;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.inject.Named;
import java.io.Serializable;

@Named
@ViewScoped
public class ProgressBarView implements Serializable {
    
    private Integer progress1;
    private Integer progress2;

    public Integer getProgress1() {
        progress1 = updateProgress(progress1);
        return progress1;
    }

    public Integer getProgress2() {
        progress2 = updateProgress(progress2);
        return progress2;
    }

    public void longRunning() throws InterruptedException {
        progress2 = 0;
        while (progress2 == null || progress2 < 100) {
            progress2 = updateProgress(progress2);
            Thread.sleep(500);
        }
    }

    private Integer updateProgress(Integer progress) {
        if(progress == null) {
            progress = 0;
        }
        else {
            progress = progress + (int)(Math.random() * 35);
            
            if(progress > 100)
                progress = 100;
        }
        
        return progress;
    }

    public void setProgress1(Integer progress1) {
        this.progress1 = progress1;
    }

    public void setProgress2(Integer progress2) {
        this.progress2 = progress2;
    }

    public void onComplete() {
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("Progress Completed"));
    }

    public void cancel() {
        progress1 = null;
        progress2 = null;
    }
}
