# Clean PDE-LAB Workspace
Write-Host "Санитарим PDE-LAB репу от мусора..." -ForegroundColor Cyan

# Сносим гредловый билд (а то кэши часто врут)
if (Test-Path "build") {
    Remove-Item -Recurse -Force "build"
    Write-Host "Снесли build/ - кэш сгорел" -ForegroundColor Green
}

if (Test-Path ".gradle") {
    Remove-Item -Recurse -Force ".gradle"
    Write-Host "Снесли .gradle/ - демоны изгнаны" -ForegroundColor Green
}

# Чистим старые тесты и выхлопы (засоряют диск, девопсы ругаются)
if (Test-Path "artifacts") {
    Remove-Item -Recurse -Force "artifacts"
    Write-Host "Снесли artifacts/ (старые логи больше не в счет)" -ForegroundColor Green
}

# Удаляем потерянные JFR слепки (забытые профилировщиком)
Get-ChildItem -Path . -Filter "*.jfr" -Recurse | Remove-Item -Force
Write-Host "Удалили сиротские *.jfr профили" -ForegroundColor Green

# Изыди, IDE! (Сносим ошметки от IntelliJ и VSCode)
$idePaths = @(".idea", ".vscode", ".bsp", "bin", "out")
foreach ($path in $idePaths) {
    if (Test-Path $path) {
        Remove-Item -Recurse -Force $path
        Write-Host "Удалили кэш IDE: $path (незачем тут плодить)" -ForegroundColor Green
    }
}

Write-Host "Репа стерильна! Готово к HPC деплою (в продакшен, детка)." -ForegroundColor Magenta
