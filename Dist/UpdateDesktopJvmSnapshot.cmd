@echo off
rem ==========================================================================
rem  AHA - one-click fetch of a DESKTOP JVM portable package snapshot build (Windows)
rem
rem  A snapshot is a build made for a PR that is not merged yet (issue #165).
rem  It lives on the release page as a pre-release, tagged like
rem      V0.1.3.00203.10151357-AHA-Desktop-JVM-SNAPSHOT
rem
rem  This launcher only calls
rem      Dist\UpdateSnapshot.ps1 -Kind desktop-jvm
rem  which queries the GitHub release page through PowerShell / .NET (Schannel
rem  TLS). No gh, no Python, no token required.
rem
rem  The kind is fixed here: do NOT pass -Kind / --kind yourself.
rem
rem  Usage:
rem    UpdateDesktopJvmSnapshot.cmd                  fetch the most recent snapshot
rem    UpdateDesktopJvmSnapshot.cmd -List            list snapshots (version + days left)
rem    UpdateDesktopJvmSnapshot.cmd -Pr 203          fetch the snapshot built for PR 203
rem    UpdateDesktopJvmSnapshot.cmd -DryRun          preview only, no download
rem    UpdateDesktopJvmSnapshot.cmd -Force           re-download even if already fetched
rem
rem  Chinese documentation: README.commands.md in this directory, section 7.
rem
rem  Conventions - enforced by bin/CheckScripts.py:
rem    * this file stays ASCII-only with CRLF line endings;
rem    * it deliberately uses NO parenthesised if-blocks: labels plus goto only.
rem      Mixing if-blocks with caret-escaped parentheses is a known CMD trap and
rem      broke an earlier revision of the other launchers - see bin/CheckScripts.py.
rem ==========================================================================

setlocal
cd /d "%~dp0"

rem Absolute path: never let a same-named program on PATH shadow PowerShell.
set "PS_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
set "PS1=%~dp0UpdateSnapshot.ps1"
set "KIND=desktop-jvm"

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
if /i "%ONE%"=="--pr" set "ONE=-Pr"
if /i "%ONE%"=="--branch" set "ONE=-Branch"
if /i "%ONE%"=="--run" set "ONE=-Run"
if /i "%ONE%"=="--out" set "ONE=-Out"
if /i "%ONE%"=="--repo" set "ONE=-Repo"
rem The kind belongs to this launcher: swallow -Kind / --kind together with its value,
rem otherwise PowerShell would reject the duplicated parameter.
if /i "%ONE%"=="-kind" goto skipkind
if /i "%ONE%"=="--kind" goto skipkind
set "ARGS=%ARGS% %ONE%"
shift
goto collect

:skipkind
shift
if "%~1"=="" goto run
shift
goto collect

:run
"%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%PS1%" -Kind %KIND% %ARGS%
rem Read the exit code right away - the next command would overwrite errorlevel.
set "RC=%errorlevel%"
echo.
if not "%RC%"=="0" goto failed
goto finish

:failed
echo [FAILED] exit code %RC% - see the message above for the reason.
echo Common causes: no network, GitHub rate limit, or no snapshot for that PR yet.

:finish
if not defined NOARGS goto done
pause

:done
exit /b %RC%

:missing
echo.
echo [ERROR] Not found: %PS1%
echo Keep UpdateDesktopJvmSnapshot.cmd and UpdateSnapshot.ps1 together in Dist.
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
