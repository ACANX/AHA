@echo off
rem
rem AHA Desktop launcher (Windows)
rem
rem Bundle layout (since issue #218): flat - the fat jar aha-desktop.jar sits
rem next to this script in the bundle root; there is no lib\ and no bin\.
rem The jar already ships this platform's OpenJFX (classes and native libraries
rem inside it, extracted and cached by JavaFX at runtime), so a JDK 25 runtime
rem is the only prerequisite.
rem
rem Also works from a source checkout: when no fat jar is found, the portable
rem bundle (Dist\aha-desktop-<version>-<platform>.zip) is extracted into a
rem directory keyed by the zip name and its timestamp, then run from there.
rem Old directories are kept: they may still be in use.
setlocal

for %%I in ("%~dp0.") do set "DIR=%%~fI"

if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
    set "JAR=%JAVA_HOME%\bin\jar.exe"
) else (
    set "JAVA=java"
    set "JAR=jar"
)

rem Fat jar may sit next to this script (bundle, or Dist\ after extraction)
rem or one level up (legacy bin\ layout).
set "JARDIR="
if exist "%DIR%\aha-desktop.jar" set "JARDIR=%DIR%"
if not defined JARDIR if exist "%DIR%\..\aha-desktop.jar" for %%I in ("%DIR%\..") do set "JARDIR=%%~fI"
if defined JARDIR goto :launch

rem -- source checkout: extract the portable bundle -----------------------
set "ROOT="
if exist "%DIR%\pom.xml" set "ROOT=%DIR%"
if not defined ROOT if exist "%DIR%\..\pom.xml" for %%I in ("%DIR%\..") do set "ROOT=%%~fI"
if not defined ROOT goto :missing

set "ZIP="
set "ZDIR="
set "ZNAME="
rem issue #269 naming first: aha-desktop-jvm-<version>-windows-amd64-jdk25.zip.
for %%Z in ("%ROOT%\Dist\aha-desktop-jvm-*.zip") do (
    if exist "%%~fZ" (
        set "ZIP=%%~fZ"
        set "ZDIR=%%~dpZ"
        set "ZNAME=%%~nZ"
    )
)
rem Legacy name / Maven artifact fallback (the [0-9] pattern skips aha-desktop-native-*.zip).
if not defined ZIP for %%Z in ("%ROOT%\Dist\aha-desktop-*-win.zip") do (
    if exist "%%~fZ" (
        set "ZIP=%%~fZ"
        set "ZDIR=%%~dpZ"
        set "ZNAME=%%~nZ"
    )
)
if not defined ZIP (
    for %%Z in ("%ROOT%\Dist\aha-desktop-[0-9]*.zip") do (
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

if not exist "%BUNDLE%\aha-desktop.jar" call :extract "%ZIP%" "%BUNDLE%" || goto :failed
if not exist "%BUNDLE%\aha-desktop.jar" goto :legacy
set "JARDIR=%BUNDLE%"

:launch
rem The fat jar runs on the classpath, so --add-opens is not needed (the
rem unnamed module is already open) and --enable-native-access must say
rem ALL-UNNAMED: naming a module would fail with "Unknown module".
"%JAVA%" --enable-native-access=ALL-UNNAMED -jar "%JARDIR%\aha-desktop.jar" %*

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
echo [ERROR] Desktop fat jar not found: "%DIR%\aha-desktop.jar" 1>&2
echo [ERROR] Build the portable bundle first:  mvnw.cmd -pl aha-desktop -am package -DskipTests 1>&2
echo [ERROR] Then run this script again; it extracts Dist\aha-desktop-*.zip automatically. 1>&2
endlocal & exit /b 1

:legacy
echo [ERROR] No aha-desktop.jar inside "%ZIP%". 1>&2
echo [ERROR] That bundle is likely the pre-#218 layout (bin/ + lib/, versioned jar). 1>&2
echo [ERROR] Rebuild with:  mvnw.cmd clean package   -- or take bundle 0.1.3.00219+. 1>&2
endlocal & exit /b 1

:failed
echo [ERROR] Failed to extract "%%ZIP%%" into "%%BUNDLE%%" 1>&2
endlocal & exit /b 1
