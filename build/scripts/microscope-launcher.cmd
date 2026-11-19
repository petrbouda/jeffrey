@echo off
rem
rem Jeffrey
rem Copyright (C) 2026 Petr Bouda
rem
rem Licensed under the Apache License, Version 2.0 (the "License");
rem you may not use this file except in compliance with the License.
rem You may obtain a copy of the License at
rem
rem     https://www.apache.org/licenses/LICENSE-2.0
rem
rem Unless required by applicable law or agreed to in writing, software
rem distributed under the License is distributed on an "AS IS" BASIS,
rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
rem See the License for the specific language governing permissions and
rem limitations under the License.
rem
rem Launches Jeffrey Microscope using the bundled jlink runtime.
rem Override with JAVA_HOME to use a different JDK.
rem Pass JVM options in JAVA_OPTS (e.g. -Xmx4g).

setlocal

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "APP_HOME=%%~fI"

if defined JAVA_HOME (
    set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_CMD=%APP_HOME%\runtime\bin\java.exe"
)

"%JAVA_CMD%" --add-modules jdk.incubator.vector %JAVA_OPTS% -jar "%APP_HOME%\lib\microscope.jar" %*
