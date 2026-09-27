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

package cafe.jeffrey.ide.plugin.idea.agent;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/** Which quoting a terminal's shell gets, and how each one spells an argument and a variable. */
public class ShellQuotingTest {

    private static final String HOSTILE = "a\"b'c&d$(rm)";
    private static final List<String> CMD_OPERATORS = List.of("&", "|", "<", ">", "^", "%");

    @Test
    public void everyShellOffWindowsIsPosix() {
        assertEquals(ShellQuoting.POSIX, ShellQuoting.forShell("/bin/zsh", false));
        assertEquals(ShellQuoting.POSIX, ShellQuoting.forShell("/usr/bin/pwsh", false));
        assertEquals(ShellQuoting.POSIX, ShellQuoting.forShell(null, false));
    }

    @Test
    public void windowsShellsAreToldApart() {
        assertEquals(ShellQuoting.POWERSHELL, ShellQuoting.forShell("powershell.exe", true));
        assertEquals(ShellQuoting.POWERSHELL,
                ShellQuoting.forShell("C:\\Program Files\\PowerShell\\7\\pwsh.exe", true));
        assertEquals(ShellQuoting.CMD, ShellQuoting.forShell("C:\\Windows\\System32\\cmd.exe", true));
        assertEquals(ShellQuoting.CMD, ShellQuoting.forShell("cmd", true));
        assertEquals(ShellQuoting.POSIX, ShellQuoting.forShell("C:\\Program Files\\Git\\bin\\bash.exe", true));
        assertEquals(ShellQuoting.POSIX, ShellQuoting.forShell("wsl.exe", true));
    }

    /** PowerShell is the IDE's default terminal shell on Windows. */
    @Test
    public void anUnknownWindowsShellIsPowerShell() {
        assertEquals(ShellQuoting.POWERSHELL, ShellQuoting.forShell(null, true));
        assertEquals(ShellQuoting.POWERSHELL, ShellQuoting.forShell(" ", true));
    }

    @Test
    public void posixClosesEscapesAndReopensASingleQuote() {
        assertEquals("'a'\\''b \"c\" $(d)'", ShellQuoting.POSIX.quote("a'b \"c\" $(d)"));
        assertEquals("V='x y' ", ShellQuoting.POSIX.environment("V", "x y"));
    }

    @Test
    public void powerShellDoublesASingleQuoteAndEscapesADoubleOne() {
        assertEquals("'a''b \\\"c\\\" $(d)'", ShellQuoting.POWERSHELL.quote("a'b \"c\" $(d)"));
        assertEquals("$env:V='x''y'; ", ShellQuoting.POWERSHELL.environment("V", "x'y"));
    }

    /** A value with every hostile character stays one inert word of a POSIX shell. */
    @Test
    public void posixKeepsAHostileVariableInert() {
        assertEquals("V='a\"b'\\''c&d$(rm)' ", ShellQuoting.POSIX.environment("V", HOSTILE));
    }

    /** In a PowerShell literal only a single quote is special, and it is doubled. */
    @Test
    public void powerShellKeepsAHostileVariableInert() {
        assertEquals("$env:V='a\"b''c&d$(rm)'; ", ShellQuoting.POWERSHELL.environment("V", HOSTILE));
    }

    /** A typographic quote closes a PowerShell literal too, so it is doubled like an apostrophe. */
    @Test
    public void powerShellDoublesTypographicQuotes() {
        assertEquals("'a\u2019\u2019;calc'", ShellQuoting.POWERSHELL.quote("a\u2019;calc"));
        assertEquals("$env:V='a\u2018\u2018b'; ", ShellQuoting.POWERSHELL.environment("V", "a\u2018b"));
    }

    /**
     * {@code set "V=http://host"&calc&""} would run {@code calc}: cmd.exe has no escape for a quote
     * there, so a value carrying one — or any operator — is refused rather than typed.
     */
    @Test
    public void cmdRefusesAHostileVariable() {
        assertThrows(IllegalArgumentException.class,
                () -> ShellQuoting.CMD.environment("V", "http://host\"&calc&\""));
        assertThrows(IllegalArgumentException.class, () -> ShellQuoting.CMD.environment("V", HOSTILE));
        assertThrows(IllegalArgumentException.class, () -> ShellQuoting.CMD.environment("V", "a\"b"));
        for (String operator : CMD_OPERATORS) {
            assertThrows(IllegalArgumentException.class, () -> ShellQuoting.CMD.environment("V", "a" + operator + "b"));
        }
    }

    /** A quote toggles cmd.exe's quoting off, so an operator anywhere in an argument is refused too. */
    @Test
    public void cmdRefusesAnOperatorInAnArgument() {
        assertThrows(IllegalArgumentException.class, () -> ShellQuoting.CMD.quote("http://host\"&calc&\""));
        for (String operator : CMD_OPERATORS) {
            assertThrows(IllegalArgumentException.class, () -> ShellQuoting.CMD.quote("a" + operator + "b"));
        }
    }

    @Test
    public void cmdAcceptsAQuoteAndDollarsInAVariable() {
        assertEquals("set \"V=it's$(rm)\" && ", ShellQuoting.CMD.environment("V", "it's$(rm)"));
    }

    /**
     * Windows programs read {@code 2n} backslashes before a quote as a delimiter and {@code 2n+1} as a
     * literal quote, so a backslash already in front of one is doubled and one more added — {@code \\"}
     * would otherwise close the argument and split it in two.
     */
    @Test
    public void cmdDoublesTheBackslashesBeforeAQuote() {
        assertEquals("\"a\\\\\\\"b\"", ShellQuoting.CMD.quote("a\\\"b"));
        assertEquals("\"a\\\\\\\\\\\"b\"", ShellQuoting.CMD.quote("a\\\\\"b"));
    }

    /** A trailing run is doubled too, so it cannot escape the closing quote. */
    @Test
    public void cmdDoublesATrailingBackslash() {
        assertEquals("\"a\\\\\"", ShellQuoting.CMD.quote("a\\"));
    }

    /** A backslash that is not in front of a quote is literal to a Windows program, and left alone. */
    @Test
    public void windowsLeavesOtherBackslashesAlone() {
        assertEquals("\"a\\b\"", ShellQuoting.CMD.quote("a\\b"));
        assertEquals("'a\\b'", ShellQuoting.POWERSHELL.quote("a\\b"));
    }

    @Test
    public void powerShellDoublesTheBackslashesBeforeAQuote() {
        assertEquals("'a\\\\\\\"b'", ShellQuoting.POWERSHELL.quote("a\\\"b"));
    }

    /** Whether PowerShell wraps the argument decides what a trailing backslash needs; it is refused. */
    @Test
    public void powerShellRefusesATrailingBackslash() {
        assertThrows(IllegalArgumentException.class, () -> ShellQuoting.POWERSHELL.quote("a\\"));
    }

    @Test
    public void cmdEscapesADoubleQuote() {
        assertEquals("\"a'b \\\"c\\\" $(d)\"", ShellQuoting.CMD.quote("a'b \"c\" $(d)"));
        assertEquals("set \"V=x y\" && ", ShellQuoting.CMD.environment("V", "x y"));
    }
}
