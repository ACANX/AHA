@echo off
rem ==========================================================================
rem  AHA - one-click update of the DESKTOP native image (Windows)
rem
rem  Thin launcher. All logic lives in the shared Python engine:
rem    Script\Python\DesktopNativeVersionUpdate.py   entry for this kind
rem    Script\Python\NativeVersionUpdate.py          shared engine (both kinds)
rem  Design / rationale: Docs/TODO/TD-00015-*.md, Docs/Design/DesktopNativeUpdateDesign.md
rem
rem  Usage:
rem    UpdateDesktopNative.cmd              update to the newest build
rem    UpdateDesktopNative.cmd --list       list recent versions
rem    UpdateDesktopNative.cmd --dry-run    preview only, no download
rem    UpdateDesktopNative.cmd --help       all options (--tag / --jdk / --platform ...)
rem
rem  Requires Python 3 (py launcher, python, or python3) and a repository clone:
rem  this launcher lives in <repo>\Dist\ and resolves the repo root from there.
rem  Without Python, download the package by hand from
rem    https://github.com/ACANX/AHA/releases
rem  (asset name: AHA-Desktop-Native-<version>-<os>-<arch>-jdk<JDK>.zip).
rem
rem  Chinese documentation: README.commands.md in this directory, section 4.1.
rem
rem  Convention (enforced by bin/CheckScripts.py): keep this file ASCII-only
rem  with CRLF line endings. Non-ASCII text belongs in the README.
rem ==========================================================================

setlocal
rem Repo root is one level above Dist\ - where this file is committed.
cd /d "%~dp0.."

set "SCRIPT=Script\Python\DesktopNativeVersionUpdate.py"

if not exist "%SCRIPT%" (
    echo.
    echo [ERROR] Not found: %SCRIPT%
    echo         This launcher must stay in ^<repo^>\Dist\ next to the repository,
    echo         or clone the repository again from
    echo         https://github.com/ACANX/AHA
    echo.
    pause
    exit /b 1
)

rem Find a working Python 3: py launcher first (Windows), then python, then python3.
rem Each candidate is probed with a real interpreter call, so a broken alias that
rem exists on PATH but cannot run is not mistaken for a usable interpreter.
set "PY="
py -3 -c "import sys" >nul 2>nul && set "PY=py -3"
if not defined PY (python -c "import sys" >nul 2>nul && set "PY=python")
if not defined PY (python3 -c "import sys" >nul 2>nul && set "PY=python3")

if not defined PY (
    echo.
    echo [ERROR] Python 3 was not found on PATH ^(tried: py -3, python, python3^).
    echo         Install Python 3, then run this file again - or download the
    echo         package by hand from https://github.com/ACANX/AHA/releases
    echo.
    pause
    exit /b 1
)

echo Using: %PY% %SCRIPT% %*
echo.

%PY% "%SCRIPT%" %*
rem Read the exit code right away - the next command would overwrite errorlevel.
set "RC=%errorlevel%"

echo.
if "%RC%"=="0" (
    echo [DONE] Installed into Dist\ ^(the previous build, if any, is kept as .bak^).
) else (
    echo [FAILED] exit code %RC% - see the output above for the reason
    echo          ^(network unreachable, GitHub rate limit, or a running program
    echo          holding the executable file^).
)

rem Pause only when launched without arguments, i.e. the double-click case;
rem with arguments the caller usually wants the shell back immediately.
if "%~1"=="" pause
exit /b %RC%
