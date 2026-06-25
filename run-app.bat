@echo off
set SDK_DIR=G:\SDK
set EMULATOR_PATH=%SDK_DIR%\emulator\emulator.exe
set ADB_PATH=%SDK_DIR%\platform-tools\adb.exe
set AVD_NAME=Small_Phone

echo =======================================================
echo  HiloApp - Lanzador del Emulador y Aplicacion
echo =======================================================
echo.

echo [1/3] Comprobando dispositivo o emulador activo...
%ADB_PATH% devices | findstr /R /C:"\device$" > nul
if %errorlevel% equ 0 (
    echo El emulador ya esta corriendo o hay un dispositivo conectado.
) else (
    echo Iniciando emulador '%AVD_NAME%' en segundo plano...
    start "" "%EMULATOR_PATH%" -avd %AVD_NAME%
    
    echo Esperando a que adb se conecte...
    %ADB_PATH% wait-for-device
    
    echo Esperando a que el sistema operativo de Android termine de iniciar...
    :wait_loop
    %ADB_PATH% shell getprop sys.boot_completed 2>nul | findstr "1" >nul
    if errorlevel 1 (
        timeout /t 2 /nobreak >nul
        goto wait_loop
    )
    echo ¡Emulador completamente iniciado y listo!
)

echo.
echo [2/3] Compilando e instalando la aplicacion en el emulador...
call gradlew.bat installDebug

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Hubo un problema al compilar o instalar la aplicacion.
    pause
    exit /b %errorlevel%
)

echo.
echo [3/3] Iniciando la aplicacion (MainActivity)...
%ADB_PATH% shell am start -n com.example.hiloapp/.MainActivity

echo.
echo =======================================================
echo  Proceso finalizado con exito. ¡A disfrutar!
echo =======================================================
echo.
pause
