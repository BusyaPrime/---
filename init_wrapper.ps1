Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.6-bin.zip" -OutFile "gradle.zip"
Expand-Archive -Path "gradle.zip" -DestinationPath "."
.\gradle-8.6\bin\gradle wrapper
Remove-Item -Path "gradle.zip"
Remove-Item -Recurse -Force "gradle-8.6"
