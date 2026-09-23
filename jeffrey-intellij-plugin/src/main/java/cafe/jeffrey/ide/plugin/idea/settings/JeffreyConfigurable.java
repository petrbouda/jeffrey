/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * Settings panel under <em>Settings → Tools → Jeffrey Plugin</em>: an enable toggle, the
 * built-in server port (which Microscope discovers by scanning), and the Microscope address the
 * "Analyze in Microscope" action opens. Access is limited to localhost; there is no token to
 * configure.
 */
public final class JeffreyConfigurable implements Configurable {

    private JBCheckBox enabledCheckbox;
    private JBTextField microscopeUrlField;
    private JBCheckBox agentsCheckbox;

    /**
     * Kept in step with the {@code displayName} of the {@code applicationConfigurable} declaration:
     * the settings tree labels the entry from the XML attribute, so a different answer here would be
     * a second name for the same panel that nothing ever displays.
     */
    @Override
    public String getDisplayName() {
        return "Jeffrey Plugin";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        enabledCheckbox = new JBCheckBox("Allow Jeffrey Microscope to connect to this IDE", currentEnabled());
        JBLabel portLabel = new JBLabel(String.valueOf(BuiltInServerManager.getInstance().getPort()));
        JBLabel accessLabel = new JBLabel("Localhost only — Microscope finds this IDE by scanning the built-in server port range.");
        microscopeUrlField = new JBTextField(currentMicroscopeUrl());
        JBLabel urlLabel = new JBLabel("Used by \"Analyze in Microscope\" to send a recording or heap dump for analysis.");
        agentsCheckbox = new JBCheckBox("Offer to analyse a profile with a coding agent", currentAgentsEnabled());
        JBLabel agentsLabel = new JBLabel("Adds buttons to a recording's tab that run Claude Code, Codex or Gemini in a terminal.");

        return FormBuilder.createFormBuilder()
                .addComponent(enabledCheckbox)
                .addLabeledComponent("Built-in server port:", portLabel)
                .addComponent(accessLabel)
                .addLabeledComponent("Microscope address:", microscopeUrlField)
                .addComponent(urlLabel)
                .addComponent(agentsCheckbox)
                .addComponent(agentsLabel)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        return enabledCheckbox.isSelected() != currentEnabled()
                || agentsCheckbox.isSelected() != currentAgentsEnabled()
                || !microscopeUrlField.getText().equals(currentMicroscopeUrl());
    }

    @Override
    public void apply() {
        JeffreySettings.getInstance().setEnabled(enabledCheckbox.isSelected());
        JeffreySettings.getInstance().setMicroscopeUrl(microscopeUrlField.getText());
        JeffreySettings.getInstance().setAgentsEnabled(agentsCheckbox.isSelected());
        // Show what was actually stored: the setting normalises a trailing slash, and a field left
        // holding the un-normalised text would keep Apply enabled with nothing left to apply.
        microscopeUrlField.setText(currentMicroscopeUrl());
    }

    @Override
    public void reset() {
        enabledCheckbox.setSelected(currentEnabled());
        agentsCheckbox.setSelected(currentAgentsEnabled());
        microscopeUrlField.setText(currentMicroscopeUrl());
    }

    private static boolean currentEnabled() {
        return JeffreySettings.getInstance().isEnabled();
    }

    private static boolean currentAgentsEnabled() {
        return JeffreySettings.getInstance().areAgentsEnabled();
    }

    private static String currentMicroscopeUrl() {
        return JeffreySettings.getInstance().microscopeUrl();
    }
}
