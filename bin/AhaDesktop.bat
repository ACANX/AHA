@echo off
rem
rem AHA Desktop launcher (Windows)
rem
rem Requires lib\ in the parent directory (JPMS module path).
rem The bundle already contains the Windows OpenJFX native libraries,
rem so a JDK 25 runtime is the only prerequisite.
rem
setlocal

for %%I in ("%~dp0..") do set "BASE=%%~fI"
set "LIB=%BASE%\lib"

if not exist "%LIB%\" (
    echo [ERROR] Module directory not found: "%LIB%" 1>&2
    echo [ERROR] Build the distribution first:  mvnw.cmd clean package 1>&2
    exit /b 1
)

if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA=java"
)

"%JAVA%" --enable-native-access=org.xerial.sqlitejdbc ^
    --module-path "%LIB%" ^
    --module com.acanx.module.aha.desktop/com.acanx.module.aha.desktop.AhaDesktopApp %*

set "RC=%ERRORLEVEL%"
endlocal & exit /b %RC%
