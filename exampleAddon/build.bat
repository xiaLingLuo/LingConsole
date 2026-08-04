@echo off
rem ============================================================
rem  LingConsole example addon - standalone build (Windows)
rem
rem  Prereq: JDK 25 (javac/jar on PATH)
rem  Dep:    libs\lingconsole-api.jar + libs\javalin.jar
rem          (only API package + Javalin compile dependency)
rem
rem  Usage:
rem    build.bat                          build exampleAddon.jar
rem ============================================================
setlocal
set "DIR=%~dp0"
if "%DIR:~-1%"=="\" set "DIR=%DIR:~0,-1%"

set "CP=%DIR%\libs\lingconsole-api.jar;%DIR%\libs\javalin.jar"
if not exist "%DIR%\libs\lingconsole-api.jar" (
  echo [ERROR] libs\lingconsole-api.jar not found.
  exit /b 1
)
if not exist "%DIR%\libs\javalin.jar" (
  echo [ERROR] libs\javalin.jar not found.
  exit /b 1
)

set "SRC=%DIR%\src\main\java"
set "OUT=%DIR%\out"
set "ADDON_JAR=%DIR%\exampleAddon.jar"

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%" >nul

echo Compiling (javac, cp=%CP%) ...
javac -encoding UTF-8 -cp "%CP%" -d "%OUT%" "%SRC%\im\xz\cn\example\addon\ExampleAddon.java"
if errorlevel 1 (
  echo [ERROR] compile failed.
  exit /b 1
)

echo Packaging (jar) ...
jar cf "%ADDON_JAR%" -C "%DIR%" addon.toml -C "%OUT%" .
if errorlevel 1 (
  echo [ERROR] packaging failed.
  exit /b 1
)

echo.
echo Built: %ADDON_JAR%
echo Put exampleAddon.jar into the LingConsole addons\ dir and restart.
endlocal

