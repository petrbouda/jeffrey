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
package pbouda.jeffrey.view.multimedia;

import javax.faces.view.ViewScoped;
import org.primefaces.event.CaptureEvent;

import javax.faces.FacesException;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.imageio.stream.FileImageOutputStream;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;

@Named
@ViewScoped
public class PhotoCamView implements Serializable {
    
    private String filename;
    
    private String getRandomImageName() {
		int i = (int) (Math.random() * 10000000);
		
		return String.valueOf(i);
	}

    public String getFilename() {
        return filename;
    }
    
    public void oncapture(CaptureEvent captureEvent) {
        filename = getRandomImageName();
        byte[] data = captureEvent.getData();

        ExternalContext externalContext = FacesContext.getCurrentInstance().getExternalContext();
        String newFileName = externalContext.getRealPath("") + File.separator + "resources" + File.separator + "demo" +
                                    File.separator + "images" + File.separator + "photocam" + File.separator + filename + ".jpeg";
		
		FileImageOutputStream imageOutput;
		try {
			imageOutput = new FileImageOutputStream(new File(newFileName));
			imageOutput.write(data, 0, data.length);
			imageOutput.close();
		}
        catch(IOException e) {
			throw new FacesException("Error in writing captured image.", e);
		}
    }
}
