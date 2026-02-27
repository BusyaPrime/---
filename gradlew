#!/bin/sh

#
# Copyright © 2015-2021 the original authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
#
#   Стартовый скрипт Gradle для POSIX систем.
#
#   ВАЖНО:
#   Тут куча хардкорной магии оболочки (POSIX shell features),
#   пацаны из Gradle писали это годами.
#   Скрипт сам вычисляет пути, чистит кавычки и ищет Java.
#   Не ломайте.
#
##############################################################################

# Пытаемся выцепить APP_HOME (где мы находимся)

# Резолвим симлинки: $0 может быть ссылкой
app_path=$0

# Крутим цикл для вложенных симлинков (daisy-chaining).
while
    APP_HOME=${app_path%"${app_path##*/}"}  # leaves a trailing /; empty if no leading path
    [ -h "$app_path" ]
do
    ls=$( ls -ld "$app_path" )
    link=${ls#*' -> '}
    case $link in             #(
      /*)   app_path=$link ;; #(
      *)    app_path=$APP_HOME$link ;;
    esac
done

# Обычно эта переменная не юзается
# shellcheck disable=SC2034
APP_BASE_NAME=${0##*/}
# Скипаем стандартный выхлоп cd (на случай если CDPATH засран) (https://github.com/gradle/gradle/issues/25036)
APP_HOME=$( cd "${APP_HOME:-./}" > /dev/null && pwd -P ) || exit

# Поднимаем лимиты файлов до небес (MAX_FD)
MAX_FD=maximum

warn () {
    echo "$*"
} >&2

die () {
    echo
    echo "$*"
    echo
    exit 1
} >&2

# Костыли под разные операционки (true/false)
cygwin=false
msys=false
darwin=false
nonstop=false
case "$( uname )" in                #(
  CYGWIN* )         cygwin=true  ;; #(
  Darwin* )         darwin=true  ;; #(
  MSYS* | MINGW* )  msys=true    ;; #(
  NONSTOP* )        nonstop=true ;;
esac

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar


# Ищем бинарник Java шоб поднять JVM.
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        # IBM-овская JDK на AIX прячет бинари в дикие места
        JAVACMD=$JAVA_HOME/jre/sh/java
    else
        JAVACMD=$JAVA_HOME/bin/java
    fi
    if [ ! -x "$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD=java
    if ! command -v java >/dev/null 2>&1
    then
        die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
fi

# Бустим лимит файловых дескрипторов (если ОС позволит).
if ! "$cygwin" && ! "$darwin" && ! "$nonstop" ; then
    case $MAX_FD in #(
      max*)
        # ulimit -H тут лотерея, так что чекаем результат ручками.
        # shellcheck disable=SC2039,SC3045
        MAX_FD=$( ulimit -H -n ) ||
            warn "Не шмагли выцепить лимит дескрипторов"
    esac
    case $MAX_FD in  #(
      '' | soft) :;; #(
      *)
        # In POSIX sh, ulimit -n is undefined. That's why the result is checked to see if it worked.
        # shellcheck disable=SC2039,SC3045
        ulimit -n "$MAX_FD" ||
            warn "Не шмагли поднять лимит дескрипторов до $MAX_FD"
    esac
fi

# Собираем все аргументы для джавы (складываем хитро):
#   * аргументы из консоли
#   * имя мэйн класса
#   * -classpath
#   * -D...appname settings
#   * --module-path (только если припрет)
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, and GRADLE_OPTS environment variables.

# Под Cygwin/MSYS конвертим пути в виндовый формат, иначе Java подавится
if "$cygwin" || "$msys" ; then
    APP_HOME=$( cygpath --path --mixed "$APP_HOME" )
    CLASSPATH=$( cygpath --path --mixed "$CLASSPATH" )

    JAVACMD=$( cygpath --unix "$JAVACMD" )

    # Конвертим аргументы (лютый костыль шоб остаться в рамках /bin/sh)
    for arg do
        if
            case $arg in                                #(
              -*)   false ;;                            # опции не трогаем #(
              /?*)  t=${arg#/} t=/${t%%/*}              # выглядит как POSIX путь
                    [ -e "$t" ] ;;                      #(
              *)    false ;;
            esac
        then
            arg=$( cygpath --path --ignore --mixed "$arg" )
        fi
        # Roll the args list around exactly as many times as the number of
        # args, so each arg winds up back in the position where it started, but
        # possibly modified.
        #
        # NB: a `for` loop captures its iteration list before it begins, so
        # changing the positional parameters here affects neither the number of
        # iterations, nor the values presented in `arg`.
        shift                   # выкидываем старый аргумент
        set -- "$@" "$arg"      # пушим конвертированный аргумент
    done
fi


# Тут накидываем дефолтные параметры JVM. Но JAVA_OPTS тоже схаваются.
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

# Тащим все опции для запуска JVM:
#   * DEFAULT_JVM_OPTS, JAVA_OPTS, JAVA_OPTS, не должны содержать куски шелла, всё будет экранировано.
#     все шелл-вставки вырежем.
#   * Например: не прокатит ${Hostname} раскрыть переменную окружения,
#     она воспримется буквально как '${Hostname}' прямо в командной строке.

set -- \
        "-Dorg.gradle.appname=$APP_BASE_NAME" \
        -classpath "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"

# Падаем в обморок, если в системе нет xargs.
if ! command -v xargs >/dev/null 2>&1
then
    die "В системе нет xargs (это фиаско, братан)"
fi

# Парсим аргументы в кавычках через xargs.
#
# Разбираем на запчасти и чистим кавычки.
#
# В Баше все было бы изи, но у нас тут голый POSIX:
#
#   readarray ARGS < <( xargs -n1 <<<"$var" ) &&
#   set -- "${ARGS[@]}" "$@"
#
# Но в POSIX нет ни массивов, ни нормальных подстановок, так что
# обрабатываем седом (as a line of input to sed) to backslash-escape any
# все спецсимволы, а затем делаем eval
# этого всего (магия) (while maintaining the separation between arguments), and wrap
# всю эту дичь заворачиваем в один скрипт "set" statement.
#
# Конечно, это сломается, если будет перенос строки
# или кривая кавычка.
#

eval "set -- $(
        printf '%s\n' "$DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS" |
        xargs -n1 |
        sed ' s~[^-[:alnum:]+,./:=@_]~\\&~g; ' |
        tr '\n' ' '
    )" '"$@"'

exec "$JAVACMD" "$@"
