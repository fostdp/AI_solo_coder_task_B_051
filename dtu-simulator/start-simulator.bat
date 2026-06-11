@echo off
chcp 65001 >nul
title 4G DTU模拟器

echo ========================================
echo    4G DTU模拟器启动脚本
echo ========================================
echo.
echo 请选择运行模式:
echo   1. 实时模式 (每2小时上报一次)
echo   2. 快速模式 (24倍速)
echo   3. 快速模式 (120倍速)
echo   4. 测试单次上报
echo   5. 查看设备列表
echo   6. 自定义参数启动
echo   0. 退出
echo.
set /p choice=请输入选项 [0-6]:

if "%choice%"=="1" goto realtime
if "%choice%"=="2" goto fast24
if "%choice%"=="3" goto fast120
if "%choice%"=="4" goto test
if "%choice%"=="5" goto list
if "%choice%"=="6" goto custom
if "%choice%"=="0" goto exit

echo 无效的选项，请重新选择
pause
goto start

:realtime
echo.
echo 正在启动实时模式...
echo 按 Ctrl+C 停止运行
echo.
npm run simulate
goto end

:fast24
echo.
echo 正在启动快速模式 (24倍速)...
echo 模拟2小时 = 实际5分钟
echo 按 Ctrl+C 停止运行
echo.
npm run simulate:fast
goto end

:fast120
echo.
echo 正在启动快速模式 (120倍速)...
echo 模拟2小时 = 实际1分钟
echo 按 Ctrl+C 停止运行
echo.
node src/index.js --mode fast --speed 120
goto end

:test
echo.
echo 正在执行单次上报测试...
echo.
node src/index.js --test-report
pause
goto end

:list
echo.
node src/index.js --list-devices
pause
goto end

:custom
echo.
echo 自定义参数启动
echo.
set /p mode=请输入模式 (realtime/fast/historical): 
set /p speed=请输入时间倍率 (默认24): 
set /p host=请输入服务器地址 (默认http://localhost:8080): 

if "%mode%"=="" set mode=realtime
if "%speed%"=="" set speed=24
if "%host%"=="" set host=http://localhost:8080

echo.
echo 正在启动... 模式: %mode%, 倍率: %speed%x, 服务器: %host%
echo.
node src/index.js --mode %mode% --speed %speed% --host %host%
goto end

:exit
echo.
echo 已退出
exit /b 0

:end
echo.
echo 程序已结束
pause
