@echo off
rem
rem AHA CLI launcher (Windows)
rem
rem Bundle layout (since issue #218): flat - the fat jar aha-cli.jar sits next to
rem this script in the bundle root; there is no lib\ and no bin\. The jar already
rem bundles every dependency, so a JDK 25 runtime is the only prerequisite.
rem
rem Also works from a source checkout: aha-cli.jar is looked up in this directory,
rem one level up, and in Dist\; if none is found, the portable bundle
rem (Dist\aha-cli-<version>[-jdk27].zip) is extracted into a directory keyed by the
rem zip name and its timestamp, then run from there. Old directories are kept:
rem they may still be in use.
setlocal

for %%I in ("%~dp0.") do set "DIR=%%~fI"

if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
    set "JAR=%JAVA_HOME%\bin\jar.exe"
) else (
    set "JAVA=java"
    set "JAR=jar"
)

rem Fat jar may sit next to this script (bundle, or Dist\ after extraction),
rem one level up (legacy bin\ layout) or in Dist\ (source checkout).
set "JARDIR="
if exist "%DIR%\aha-cli.jar" set "JARDIR=%DIR%"
if not defined JARDIR if exist "%DIR%\..\aha-cli.jar" for %%I in ("%DIR%\..") do set "JARDIR=%%~fI"
if not defined JARDIR if exist "%DIR%\..\Dist\aha-cli.jar" for %%I in ("%DIR%\..\Dist") do set "JARDIR=%%~fI"
if not defined JARDIR if exist "%DIR%\Dist\aha-cli.jar" for %%I in ("%DIR%\Dist") do set "JARDIR=%%~fI"
if defined JARDIR goto :launch

rem -- source checkout: extract the portable bundle -----------------------
set "ROOT="
if exist "%DIR%\pom.xml" set "ROOT=%DIR%"
if not defined ROOT if exist "%DIR%\..\pom.xml" for %%I in ("%DIR%\..") do set "ROOT=%%~fI"
if not defined ROOT goto :missing

set "ZIP="
set "ZDIR="
set "ZNAME="
rem issue #269 naming: aha-cli-jvm-<version>-<platform>-jdk25.zip comes first.
for %%Z in ("%ROOT%\Dist\aha-cli-jvm-*.zip") do (
    if exist "%%~fZ" (
        set "ZIP=%%~fZ"
        set "ZDIR=%%~dpZ"
        set "ZNAME=%%~nZ"
    )
)
rem Legacy name: aha-cli-<version>[-jdk27].zip.
rem The [0-9] pattern skips aha-cli-native-<version>-<jdk>.zip.
if not defined ZIP for %%Z in ("%ROOT%\Dist\aha-cli-[0-9]*.zip") do (
    if exist "%%~fZ" (
        set "ZIP=%%~fZ"
        set "ZDIR=%%~dpZ"
        set "ZNAME=%%~nZ"
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

if not exist "%BUNDLE%\aha-cli.jar" call :extract "%ZIP%" "%BUNDLE%" || goto :failed
if not exist "%BUNDLE%\aha-cli.jar" goto :legacy
set "JARDIR=%BUNDLE%"

:launch
rem The fat jar runs on the classpath, so --add-opens is not needed and
rem --enable-native-access must say ALL-UNNAMED: naming a module would fail
rem with "Unknown module".
"%JAVA%" --enable-native-access=ALL-UNNAMED -jar "%JARDIR%\aha-cli.jar" %*

set "RC=%ERRORLEVEL%"
endlocal & exit /b %RC%

:extract
echo [INFO] Extracting CLI bundle: "%~1"
if exist "%~2" rd /s /q "%~2"
mkdir "%~2" || exit /b 1
pushd "%~2" || exit /b 1
"%JAR%" --extract --file "%~1"
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%

:missing
echo [ERROR] CLI fat jar not found: "%DIR%\aha-cli.jar" 1>&2
echo [ERROR] Build the distribution first:  mvnw.cmd -pl aha-cli -am package -DskipTests 1>&2
echo [ERROR] Then run this script again; it extracts Dist\aha-cli-*.zip automatically. 1>&2
endlocal & exit /b 1

:legacy
echo [ERROR] No aha-cli.jar inside "%ZIP%". 1>&2
echo [ERROR] That bundle is likely the pre-#218 layout (bin/ + lib/, no fat jar). 1>&2
echo [ERROR] Rebuild with:  mvnw.cmd clean package   -- or take bundle 0.1.3.00221+. 1>&2
endlocal & exit /b 1

:failed
echo [ERROR] Failed to extract "%%ZIP%%" into "%%BUNDLE%%" 1>&2
endlocal & exit /b 1
