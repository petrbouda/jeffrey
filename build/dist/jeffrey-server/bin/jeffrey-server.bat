@echo off
rem
rem Jeffrey
rem Copyright (C) 2026 Petr Bouda
rem
rem This program is free software: you can redistribute it and/or modify
rem it under the terms of the GNU Affero General Public License as published by
rem the Free Software Foundation, either version 3 of the License, or
rem (at your option) any later version.
rem
rem This program is distributed in the hope that it will be useful,
rem but WITHOUT ANY WARRANTY; without even the implied warranty of
rem MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
rem GNU Affero General Public License for more details.
rem
rem You should have received a copy of the GNU Affero General Public License
rem along with this program.  If not, see <http://www.gnu.org/licenses/>.
rem
rem Launcher for the Jeffrey multi-workspace server.
rem Requires a Java 25+ runtime on PATH (or JAVA_HOME set). Extra JVM options can be
rem passed through JAVA_OPTS.

setlocal
set "SCRIPT_DIR=%~dp0"
set "APP_JAR=%SCRIPT_DIR%..\lib\jeffrey-server.jar"

if defined JAVA_HOME (
    set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_BIN=java"
)

"%JAVA_BIN%" %JAVA_OPTS% -jar "%APP_JAR%" %*
endlocal
