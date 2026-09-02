@echo off
echo Creating Virtual Drive Z: for Shyna Caller Guard...
subst Z: "%~dp0"
if %errorlevel% equ 0 (
    echo.
    echo SUCCESS! Virtual Drive Z: Created.
    echo NOW: Open Android Studio and open the project from Z:\
) else (
    echo.
    echo ERROR: Could not create drive. Try running as Administrator or check if Z: is already in use.
)
pause
