/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package cafe.jeffrey.ide.plugin.idea.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.BuiltInServerManager;

import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Settings panel under <em>Settings → Tools → Jeffrey Microscope Plugin</em>: an enable toggle, the
 * built-in server port (which Microscope discovers by scanning), and the Microscope address the
 * "Analyze in Microscope" action opens. Access is limited to localhost; there is no token to
 * configure.
 */
public final class JeffreyConfigurable implements Configurable {

    private JBCheckBox enabledCheckbox;
    private JBTextField microscopeUrlField;

    @Override
    public String getDisplayName() {
        return "Jeffrey Microscope Plugin";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        enabledCheckbox = new JBCheckBox("Allow Jeffrey Microscope to connect to this IDE", currentEnabled());
        JBLabel portLabel = new JBLabel(String.valueOf(BuiltInServerManager.getInstance().getPort()));
        JBLabel accessLabel = new JBLabel("Localhost only — Microscope finds this IDE by scanning the built-in server port range.");
        microscopeUrlField = new JBTextField(currentMicroscopeUrl());
        JBLabel urlLabel = new JBLabel("Used by \"Analyze in Microscope\" to send a recording or heap dump for analysis.");

        return FormBuilder.createFormBuilder()
                .addComponent(enabledCheckbox)
                .addLabeledComponent("Built-in server port:", portLabel)
                .addComponent(accessLabel)
                .addLabeledComponent("Microscope address:", microscopeUrlField)
                .addComponent(urlLabel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        return enabledCheckbox.isSelected() != currentEnabled()
                || !microscopeUrlField.getText().equals(currentMicroscopeUrl());
    }

    @Override
    public void apply() {
        JeffreySettings.getInstance().setEnabled(enabledCheckbox.isSelected());
        JeffreySettings.getInstance().setMicroscopeUrl(microscopeUrlField.getText());
        // Show what was actually stored: the setting normalises a trailing slash, and a field left
        // holding the un-normalised text would keep Apply enabled with nothing left to apply.
        microscopeUrlField.setText(currentMicroscopeUrl());
    }

    @Override
    public void reset() {
        enabledCheckbox.setSelected(currentEnabled());
        microscopeUrlField.setText(currentMicroscopeUrl());
    }

    private static boolean currentEnabled() {
        return JeffreySettings.getInstance().isEnabled();
    }

    private static String currentMicroscopeUrl() {
        return JeffreySettings.getInstance().microscopeUrl();
    }
}
