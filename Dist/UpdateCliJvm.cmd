@echo off
rem ==========================================================================
rem  AHA - one-click fetch of the CLI JVM portable package (Windows)
rem
rem  This launcher only calls
rem      Dist\UpdateJvm.ps1 -Kind Cli
rem  which talks to the GitHub release page through PowerShell / .NET
rem  (Schannel TLS), so a broken Python install cannot break the update.
rem
rem  Usage:
rem    UpdateCliJvm.cmd                  fetch the newest build
rem    UpdateCliJvm.cmd -List            list recent versions      (--list works too)
rem    UpdateCliJvm.cmd -DryRun          preview only, no download (--dry-run)
rem    UpdateCliJvm.cmd -Force           re-download even if already newest
rem    UpdateCliJvm.cmd -Jdk 27          JDK branch, default 25
rem    UpdateCliJvm.cmd -Tag V0.1.5.00346-aha-cli-jvm
rem    (the old combined tag V<version>-aha-jvm is still readable)
rem    (the CLI package is platform independent - no -Platform needed)

rem  Chinese documentation: README.commands.md in this directory.
rem
rem  Conventions - both are enforced by bin/CheckScripts.py:
rem    * this file stays ASCII-only with CRLF line endings;
rem    * it deliberately uses NO parenthesised if-blocks: labels plus goto only.
rem      Mixing if-blocks with caret-escaped parentheses is a known CMD trap and
rem      broke an earlier revision of the native launcher - see bin/CheckScripts.py.
rem ==========================================================================

setlocal
rem This file is committed in <repo>\Dist\ together with UpdateJvm.ps1.
cd /d "%~dp0"

rem Absolute path: never let a same-named program on PATH shadow PowerShell.
set "PS_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
set "PS1=%~dp0UpdateJvm.ps1"

if not exist "%PS1%" goto missing
if not exist "%PS_EXE%" goto nops

rem Remember whether this looks like a double-click with no arguments: only then pause.
set "NOARGS=1"
if not "%~1"=="" set "NOARGS="

rem Translate the Python-style option spellings; pass everything else through.
set "ARGS="
:collect
if "%~1"=="" goto run
set "ONE=%~1"
if /i "%ONE%"=="--list" set "ONE=-List"
if /i "%ONE%"=="--dry-run" set "ONE=-DryRun"
if /i "%ONE%"=="--force" set "ONE=-Force"
if /i "%ONE%"=="--jdk" set "ONE=-Jdk"
if /i "%ONE%"=="--tag" set "ONE=-Tag"
if /i "%ONE%"=="--platform" set "ONE=-Platform"
if /i "%ONE%"=="--out" set "ONE=-Out"
set "ARGS=%ARGS% %ONE%"
shift
goto collect

:run
"%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%PS1%" -Kind Cli %ARGS%
rem Read the exit code right away - the next command would overwrite errorlevel.
set "RC=%errorlevel%"
echo.
if not "%RC%"=="0" goto failed
goto finish

:failed
echo [FAILED] exit code %RC% - see the message above for the reason.
echo Common causes: no network, GitHub rate limit, write permission, or a full disk.

:finish
if not defined NOARGS goto done
pause

:done
exit /b %RC%

:missing
echo.
echo [ERROR] Not found: %PS1%
echo Keep UpdateCliJvm.cmd and UpdateJvm.ps1 together in Dist.
echo Clone the repository again from https://github.com/ACANX/AHA
echo.
pause
exit /b 1

:nops
echo.
echo [ERROR] Windows PowerShell not found: %PS_EXE%
echo This launcher needs Windows PowerShell 5.1, shipped with Windows 10 and 11.
echo.
pause
exit /b 1
