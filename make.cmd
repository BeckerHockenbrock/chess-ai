@echo off
where mingw32-make >nul 2>nul
if %errorlevel%==0 (
    mingw32-make %*
    exit /b %errorlevel%
)
where make >nul 2>nul
if %errorlevel%==0 (
    make %*
    exit /b %errorlevel%
)
if exist "C:\msys64\ucrt64\bin\mingw32-make.exe" (
    "C:\msys64\ucrt64\bin\mingw32-make.exe" %*
    exit /b %errorlevel%
)
if exist "C:\msys64\usr\bin\make.exe" (
    "C:\msys64\usr\bin\make.exe" %*
    exit /b %errorlevel%
)
echo Neither 'make' nor 'mingw32-make' was found in PATH or C:\msys64.
exit /b 1
