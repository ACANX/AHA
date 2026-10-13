@echo off
rem ==========================================================================
rem  AHA - one-click update of the DESKTOP native image (Windows)
rem
rem  Windows-native implementation, no Python required: this launcher only calls
rem      Dist\UpdateNative.ps1 -Kind Desktop
rem  which talks to the GitHub release page through PowerShell / .NET (Schannel
rem  TLS). It therefore does not touch Python's OpenSSL, so a broken Python
rem  install cannot break the update.
rem
rem  Usage:
rem    UpdateDesktopNative.cmd                update to the newest build
rem    UpdateDesktopNative.cmd -List          list recent versions
rem    UpdateDesktopNative.cmd -DryRun        preview only, no download
rem    UpdateDesktopNative.cmd -Force         reinstall even if already newest
rem    UpdateDesktopNative.cmd -Tag V0.1.3.00157-aha-desktop-native
rem    UpdateDesktopNative.cmd -Jdk 27        JDK branch (default 25)
rem  The Python-style spellings --list / --dry-run / --force / --jdk / --tag /
rem  --platform are translated to the PowerShell ones.
rem
rem  Chinese documentation: README.commands.md in this directory, section 4.1.
rem
rem  Convention (enforced by bin/CheckScripts.py): this file must stay ASCII-only
rem  with CRLF line endings - Chinese text belongs in UpdateNative.ps1 / README.
rem ==========================================================================

setlocal EnableExtensions
rem This file is committed in <repo>\Dist\ together with UpdateNative.ps1.
cd /d "%~dp0"

rem Absolute path: never let a same-named program on PATH shadow PowerShell.
set "PS_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
set "PS1=%~dp0UpdateNative.ps1"

if not exist "%PS1%" (
    echo.
    echo [ERROR] Not found: %PS1%
    echo         UpdateDesktopNative.cmd and UpdateNative.ps1 must stay together in Dist\.
    echo         Clone the repository again from https://github.com/ACANX/AHA
    echo.
    pause
    exit /b 1
)
if not exist "%PS_EXE%" (
    echo.
    echo [ERROR] Windows PowerShell not found: %PS_EXE%
    echo         This launcher needs Windows PowerShell 5.1 (shipped with Windows 10/11).
    echo.
    pause
    exit /b 1
)

rem Remember whether this looks like a double-click (no arguments): only then pause.
set "NOARGS="
if "%~1"=="" set "NOARGS=1"

rem Translate the Python-style option spellings, pass everything else through.
set "ARGS="
:collect
if "%~1"=="" goto ready
set "ONE=%~1"
if /i "%ONE%"=="--list" set "ONE=-List"
if /i "%ONE%"=="--dry-run" set "ONE=-DryRun"
if /i "%ONE%"=="--force" set "ONE=-Force"
if /i "%ONE%"=="--jdk" set "ONE=-Jdk"
if /i "%ONE%"=="--tag" set "ONE=-Tag"
if /i "%ONE%"=="--platform" set "ONE=-Platform"
set "ARGS=%ARGS% %ONE%"
shift
goto collect

:ready
echo Checking the latest release on GitHub, please wait...
echo.
"%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%PS1%" -Kind Desktop %ARGS%
rem Read the exit code right away - the next command would overwrite errorlevel.
set "RC=%errorlevel%"

echo.
if "%RC%"=="0" (
    echo [DONE] See the messages above ^(the previous build, if any, is kept as .bak^).
) else (
    echo [FAILED] exit code %RC% - see the message above for the reason
    echo          ^(no network, GitHub rate limit, or the target file is running^).
)
if defined NOARGS pause
exit /b %RC%
