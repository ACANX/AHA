@echo off
rem ==========================================================================
rem  AHA - one-click fetch of a snapshot build (Windows)
rem
rem  A snapshot is a build made for a PR that is not merged yet (issue #165).
rem  It lives on the release page as a pre-release, tagged like
rem      V0.1.3.00183.10141829-AHA-Desktop-Native-SNAPSHOT
rem
rem  This launcher only calls
rem      Dist\UpdateSnapshot.ps1
rem  which queries the GitHub release page through PowerShell / .NET (Schannel
rem  TLS). No gh, no Python, no token required.
rem
rem  Usage:
rem    UpdateSnapshot.cmd                       fetch the most recent snapshot
rem    UpdateSnapshot.cmd -List                 list snapshots (version + days left)
rem    UpdateSnapshot.cmd -Pr 165               fetch the snapshot built for PR 165
rem    UpdateSnapshot.cmd -Kind jvm             only JVM portable packages
rem    UpdateSnapshot.cmd -Kind desktop-native  only native desktop packages
rem    UpdateSnapshot.cmd -DryRun               preview only, no download
rem    UpdateSnapshot.cmd -Force                re-download even if already fetched
rem
rem  Chinese documentation: README.commands.md in this directory, section 7.
rem
rem  Conventions - both are enforced by bin/CheckScripts.py:
rem    * this file stays ASCII-only with CRLF line endings;
rem    * it deliberately uses NO parenthesised if-blocks: labels plus goto only.
rem      Mixing if-blocks with caret-escaped parentheses is a known CMD trap and
rem      broke an earlier revision of the other launchers - see bin/CheckScripts.py.
rem ==========================================================================

setlocal
rem This file is committed in <repo>\Dist\ together with UpdateSnapshot.ps1.
cd /d "%~dp0"

rem Absolute path: never let a same-named program on PATH shadow PowerShell.
set "PS_EXE=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
set "PS1=%~dp0UpdateSnapshot.ps1"

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
if /i "%ONE%"=="--kind" set "ONE=-Kind"
if /i "%ONE%"=="--out" set "ONE=-Out"
if /i "%ONE%"=="--repo" set "ONE=-Repo"
set "ARGS=%ARGS% %ONE%"
shift
goto collect

:run
echo Checking the release page for snapshots, please wait...
echo.
"%PS_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%PS1%" %ARGS%
rem Read the exit code right away - the next command would overwrite errorlevel.
set "RC=%errorlevel%"
echo.
if not "%RC%"=="0" goto failed
echo [DONE] See the messages above.
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
echo Keep UpdateSnapshot.cmd and UpdateSnapshot.ps1 together in Dist.
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
