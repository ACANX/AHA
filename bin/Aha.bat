@echo off
rem
rem AHA CLI launcher (Windows)
rem
rem Requires lib\ in the parent directory (JPMS module path).
rem Build it with:  mvnw.cmd clean package
rem
setlocal

for %%I in ("%~dp0..") do set "BASE=%%~fI"
set "LIB=%BASE%\lib"

if not exist "%LIB%\" (
    echo [ERROR] Module directory not found: "%LIB%" 1>&2
    echo [ERROR] Build the distribution first:  mvnw.cmd clean package 1>&2
    echo [ERROR] Or run directly from source:  mvnw.cmd -pl aha-cli exec:java 1>&2
    exit /b 1
)

if defined JAVA_HOME (
    set "JAVA=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA=java"
)

"%JAVA%" --enable-native-access=org.xerial.sqlitejdbc,org.jline ^
    --module-path "%LIB%" ^
    --module com.acanx.module.aha.cli/com.acanx.module.aha.cli.AhaCli %*

set "RC=%ERRORLEVEL%"
endlocal & exit /b %RC%
