REM LingConsole - A Server WebUI control panel
REM Copyright (C) 2026  XIAZHIRUI HUANG
REM 
REM This program is free software: you can redistribute it and/or modify
REM it under the terms of the GNU Affero General Public License as published
REM by the Free Software Foundation, either version 3 of the License, or
REM (at your option) any later version.
REM 
REM This program is distributed in the hope that it will be useful,
REM but WITHOUT ANY WARRANTY; without even the implied warranty of
REM MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
REM GNU Affero General Public License for more details.
REM 
REM You should have received a copy of the GNU Affero General Public License
REM along with this program.  If not, see <https://www.gnu.org/licenses/>.
@echo off
chcp 65001

setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "CFG=%SCRIPT_DIR%config.txt"


set "JAR_NAME=LingConsole.jar"
set "JAVA_PATH=default"
set "MAX_RAM=auto"
set "MIN_RAM=auto"
set "WEB_ON=true"
set "DAMON_ON=true"
set "SINGLE_USER_MODE=true"

if exist "%CFG%" (
  for /f "usebackq tokens=1,* delims==" %%a in ("%CFG%") do (
    if "%%a"=="jarName"    set "JAR_NAME=%%b"
    if "%%a"=="java_path"  set "JAVA_PATH=%%b"
    if "%%a"=="MaxRAM"     set "MAX_RAM=%%b"
    if "%%a"=="MinRAM"     set "MIN_RAM=%%b"
    if "%%a"=="web"        set "WEB_ON=%%b"
    if "%%a"=="damon"      set "DAMON_ON=%%b"
    if "%%a"=="singleUserMode" set "SINGLE_USER_MODE=%%b"
  )
)
set "JAR=%SCRIPT_DIR%%JAR_NAME%"


if /i "%JAVA_PATH%"=="default" (
  set "JAVA_CMD=java"
) else (
  set "JAVA_CMD=%JAVA_PATH%"
)

if not exist "%JAR%" (
  echo [ERROR] %JAR_NAME% not found.
  echo         Place the downloaded %JAR_NAME% in: %SCRIPT_DIR%
  echo.
  pause
  exit /b 1
)

if /i "%JAVA_CMD%"=="java" (
  where java >nul 2>nul
  if errorlevel 1 (
    echo [ERROR] java not found in PATH. Install JDK 25 or set java_path in config.txt.
    echo.
    pause
    exit /b 1
  )
) else (
  if not exist "%JAVA_CMD%" (
    echo [ERROR] java not found at: %JAVA_CMD%
    echo         Please fix java_path in config.txt.
    echo.
    pause
    exit /b 1
  )
)

set "JVM_ARGS=-XX:+ExitOnOutOfMemoryError"
if /i not "%MIN_RAM%"=="auto" if not "%MIN_RAM%"=="" set "JVM_ARGS=%JVM_ARGS% -Xms%MIN_RAM%"
if /i not "%MAX_RAM%"=="auto" if not "%MAX_RAM%"=="" set "JVM_ARGS=%JVM_ARGS% -Xmx%MAX_RAM%"

set "APP_ARGS="
if /i not "%WEB_ON%"=="true" set "APP_ARGS=%APP_ARGS% --webui false"
if /i not "%DAMON_ON%"=="true" set "APP_ARGS=%APP_ARGS% --damon false"
if /i not "%SINGLE_USER_MODE%"=="true" set "APP_ARGS=%APP_ARGS% --singleUserMode false"

echo ============================================================
echo   LingConsole one-click launcher
echo   JAR     : %JAR%
echo   JAVA    : %JAVA_CMD%
echo   JVM     : %JVM_ARGS%
echo   APP     : %APP_ARGS%
echo ============================================================
echo.

"%JAVA_CMD%" %JVM_ARGS% -jar "%JAR%" %APP_ARGS% %*

echo.
echo [LingConsole exited]
pause
endlocal
