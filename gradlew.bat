@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Стартовый скрипт Gradle для Винды (крутим-вертим)
@rem
@rem ##########################################################################

@rem Изолируем переменные чисто под текущую сессию (Windows NT)
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem Эта переменная обычно тут не юзается
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Схлопываем "." и ".." в APP_HOME шоб путь был нормальным.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Тут накидываем дефолтные параметры JVM. JAVA_OPTS и GRADLE_OPTS тоже схаваются.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Ищем бинарник java.exe по сусекам
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ФЕЙЛ: JAVA_HOME не задан, а 'java' не найдена в PATH. (Сорри) 1>&2
echo. 1>&2
echo Пропиши уже наконец JAVA_HOME в переменных среды, 1>&2
echo чтобы он смотрел на твою Java. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ОШИБКА: JAVA_HOME смотрит в какую-то помойку: %JAVA_HOME% 1>&2
echo. 1>&2
echo Пропиши уже наконец JAVA_HOME в переменных среды, 1>&2
echo чтобы он смотрел на твою Java. 1>&2

goto fail

:execute
@rem Собираем всё в одну убойную команду

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar


@rem Запускаем шарманку (Gradle)
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:end
@rem Выходим из local scope для виндовой консоли
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Дергай GRADLE_EXIT_CONSOLE если тебе нужен return code скрипта, а не
rem глупый код от cmd.exe /c!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
