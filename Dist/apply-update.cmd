@echo off
rem ============================================================================
rem AHA update helper entry (Windows; issue #51 section 4.5)
rem
rem   apply-update.cmd <update-plan.json>
rem
rem Why a separate entry process: the running app cannot reliably replace its own
rem files (on Windows a used .exe / .jar is usually locked). The app writes the
rem plan, launches this script, then exits. The script waits for the app to exit,
rem then backs up / replaces / self-verifies / relaunches, and writes
rem update-result.json for the next startup to show (success / failure / rollback).
rem
rem This file must stay PURE ASCII + CRLF (rule enforced by bin/CheckScripts.py):
rem CMD parses batch files with the ANSI code page, so UTF-8 Chinese bytes here
rem would be misread. All Chinese messages live in AhaApplyUpdate.ps1.
rem
rem The only argument is the plan file; nothing here parses user input (no injection).
rem ============================================================================
setlocal
set "PLAN=%~1"
if "%PLAN%"=="" (
  echo apply-update: missing plan file argument 1>&2
  exit /b 2
)
if not exist "%PLAN%" (
  echo apply-update: plan file not found: "%PLAN%" 1>&2
  exit /b 2
)

set "SCRIPT=%~dp0AhaApplyUpdate.ps1"
if not exist "%SCRIPT%" (
  echo apply-update: helper script not found: "%SCRIPT%" 1>&2
  exit /b 2
)

rem -NoProfile / -ExecutionPolicy Bypass: a per-user policy must not block the helper.
rem A machine-wide policy still wins; that case is no longer silent -- the app waits
rem for the helper-started marker and reports an error instead of exiting blind.
powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%SCRIPT%" -Plan "%PLAN%"
exit /b %ERRORLEVEL%
