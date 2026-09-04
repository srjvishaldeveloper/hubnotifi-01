# Windows PowerShell Local Runner Script for WhatsMine Application (PHP-Off)
# Usage: .\scripts\start-local.ps1

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host " Starting WhatsMine Application (Spring Boot + React - PHP Off)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Clear any conflicting tool options
$env:JAVA_TOOL_OPTIONS=""

# 2. Check Java Version
Write-Host "`n[1/3] Checking Java runtime environment..." -ForegroundColor Yellow
java -version

# 3. Launch Java Spring Boot Backend in Background
Write-Host "`n[2/3] Starting Java Spring Boot Backend on http://localhost:8080 ..." -ForegroundColor Yellow
$gradleCmd = Test-Path "$PSScriptRoot\..\java-backend\gradlew.bat" ? ".\gradlew.bat" : "C:\Users\Dell\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat"
$javaProcess = Start-Process powershell -ArgumentList "-NoExit -Command `"cd '$PSScriptRoot\..\java-backend'; `$env:JAVA_TOOL_OPTIONS=''; & 'C:\Users\Dell\.gradle\wrapper\dists\gradle-8.9-bin\90cnw93cvbtalezasaz0blq0a\gradle-8.9\bin\gradle.bat' bootRun --args='--spring.profiles.active=dev'`"" -PassThru

# 4. Launch React Frontend Dev Server
Write-Host "`n[3/3] Starting React Frontend Dev Server on http://localhost:5173 ..." -ForegroundColor Yellow
$reactProcess = Start-Process powershell -ArgumentList "-NoExit -Command `"cd '$PSScriptRoot\..\php'; npm run dev`"" -PassThru

Write-Host "`n============================================================" -ForegroundColor Green
Write-Host " Application processes launched successfully!" -ForegroundColor Green
Write-Host " Frontend:  http://localhost:5173" -ForegroundColor Green
Write-Host " Backend:   http://localhost:8080" -ForegroundColor Green
Write-Host " WebSocket: ws://localhost:8080/app/whatsmine-key" -ForegroundColor Green
Write-Host " PHP Process Required: NO" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Green
