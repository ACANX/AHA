@echo off
rem
rem AHA Desktop launcher (Windows)
rem
rem Supported layouts:
rem   1) Distribution bundle: <bundle>\lib holds the aha-desktop module and
rem      this script lives in <bundle>\bin\.
rem   2) Source checkout: no <root>\lib; the launcher extracts the portable
rem      bundle (<root>\Dist\aha-desktop-<version>-win.zip) into a directory
rem      keyed by the zip name and its timestamp, then runs from there.
rem
rem A JDK 25 runtime is the only prerequisite: the bundle already ships this
rem platform's OpenJFX native libraries.
rem
setlocal

for %%I in ("%~dp0..") do set "BASE=%%~fI"
set "LIB=%BASE%\lib"

if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
    set "JAR=%JAVA_HOME%\bin\jar.exe"
) else (
    set "JAVA=java"
    set "JAR=jar"
)

rem -- 1) distribution bundle ----------------------------------------------
if exist "%LIB%\aha-desktop-*.jar" goto :launch

rem -- 2) source checkout: extract the portable bundle ---------------------
if not exist "%BASE%\pom.xml" goto :missing

set "ZIP="
set "ZDIR="
set "ZNAME="
rem Prefer the Windows package; the [0-9] fallback skips aha-desktop-native-*.zip.
for %%Z in ("%BASE%\Dist\aha-desktop-*-win.zip") do (
    if exist "%%~fZ" (
        set "ZIP=%%~fZ"
        set "ZDIR=%%~dpZ"
        set "ZNAME=%%~nZ"
    )
)
if not defined ZIP (
    for %%Z in ("%BASE%\Dist\aha-desktop-[0-9]*.zip") do (
        if exist "%%~fZ" (
            set "ZIP=%%~fZ"
            set "ZDIR=%%~dpZ"
            set "ZNAME=%%~nZ"
        )
    )
)
if not defined ZIP goto :missing

rem Bundle directory keyed by zip name + write time + size, so a rebuilt
rem package lands in a fresh directory and is never shadowed by a stale
rem extraction. Old directories are intentionally kept: they may still be in
rem use by a running instance.
for %%A in ("%ZIP%") do set "STAMP=%%~tA-%%~zA"
set "STAMP=%STAMP: =%"
set "STAMP=%STAMP:/=%"
set "STAMP=%STAMP::=%"
set "STAMP=%STAMP:,=%"
set "STAMP=%STAMP:.=%"
set "BUNDLE=%ZDIR%%ZNAME%.%STAMP%"

if not exist "%BUNDLE%\lib\aha-desktop-*.jar" call :extract "%ZIP%" "%BUNDLE%" || goto :failed
set "LIB=%BUNDLE%\lib"

:launch
rem --add-opens below serves the render diagnostics (issue #48): reading the font
rem factory (com.sun.javafx.font.PrismFontFactory) needs reflection, and the
rem module system blocks it unless that package is opened. Harmless otherwise.
"%JAVA%" --enable-native-access=org.xerial.sqlitejdbc ^
    --add-opens javafx.graphics/com.sun.javafx.font=ALL-UNNAMED ^
    --module-path "%LIB%" ^
    --module com.acanx.module.aha.desktop/com.acanx.module.aha.desktop.AhaDesktopApp %*

set "RC=%ERRORLEVEL%"
endlocal & exit /b %RC%

:extract
echo [INFO] Extracting desktop bundle: "%~1"
if exist "%~2" rd /s /q "%~2"
mkdir "%~2" || exit /b 1
pushd "%~2" || exit /b 1
"%JAR%" --extract --file "%~1"
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%

:missing
echo [ERROR] Desktop module directory not found: "%LIB%" 1>&2
echo [ERROR] Build the portable bundle first:  mvnw.cmd -pl aha-desktop -am package -DskipTests 1>&2
echo [ERROR] Then run this script again; it extracts Dist\aha-desktop-*.zip automatically. 1>&2
endlocal & exit /b 1

:failed
echo [ERROR] Failed to extract "%ZIP%" into "%BUNDLE%" 1>&2
endlocal & exit /b 1
