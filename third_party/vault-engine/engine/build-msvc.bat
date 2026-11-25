@echo off
rem ============================================================
rem  Build vaulttool.exe (vault loot prediction engine) with MSVC
rem  Usage: build-msvc.bat
rem  Env:   VCVARS_PATH  (optional) full path to vcvars64.bat
rem ============================================================
setlocal
cd /d "%~dp0"

set "CUBIOMES=%~dp0..\cubiomes"
if not exist "%CUBIOMES%\loot\loot_table_context.c" (
    echo [ERROR] cubiomes sources not found at "%CUBIOMES%"
    exit /b 1
)

if defined VSCMD_ARG_TGT_ARCH goto have_env
set "VCVARS=%VCVARS_PATH%"
if defined VCVARS goto call_vcvars
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" goto no_vs
"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath > "%TEMP%\fo_vsdir.txt" 2>nul
set /p VSDIR=<"%TEMP%\fo_vsdir.txt"
if not defined VSDIR goto no_vs
set "VCVARS=%VSDIR%\VC\Auxiliary\Build\vcvars64.bat"
:call_vcvars
call "%VCVARS%" >nul
if errorlevel 1 goto no_vs
:have_env

if not exist build mkdir build
set "TRIAL=%CUBIOMES%\loot\loot_tables\chests\trial_chambers"
cl /nologo /O2 /std:c11 /D_CRT_SECURE_NO_WARNINGS ^
   /I "%CUBIOMES%" /I "%CUBIOMES%\loot" /I "%CUBIOMES%\loot\cjson" ^
   /Fo:build\ /Fe:build\vaulttool.exe ^
   vaulttool.c ^
   "%CUBIOMES%\loot\loot_table_context.c" ^
   "%CUBIOMES%\loot\loot_functions.c" ^
   "%CUBIOMES%\loot\loot_table_parser.c" ^
   "%CUBIOMES%\loot\items.c" ^
   "%CUBIOMES%\loot\cjson\cJSON.c" ^
   "%TRIAL%\reward_1_21_1.c" ^
   "%TRIAL%\reward_common_1_21_1.c" ^
   "%TRIAL%\reward_rare_1_21_1.c" ^
   "%TRIAL%\reward_unique_1_21_1.c" ^
   "%TRIAL%\reward_ominous_1_21_1.c" ^
   "%TRIAL%\reward_ominous_common_1_21_1.c" ^
   "%TRIAL%\reward_ominous_rare_1_21_1.c" ^
   "%TRIAL%\reward_ominous_unique_1_21_1.c"
if errorlevel 1 (
    echo [ERROR] build failed
    exit /b 1
)
echo [OK] built: %~dp0build\vaulttool.exe
endlocal
exit /b 0

:no_vs
echo [ERROR] vcvars64.bat not found.
echo         Install Visual Studio Build Tools (C++ x64) or set VCVARS_PATH.
exit /b 1
