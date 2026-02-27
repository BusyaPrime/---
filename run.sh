#!/bin/bash
if [ "$#" -ne 1 ]; then
    echo "Эй, джуниор! Использование: ./run.sh <config_file.json>"
    exit 1
fi

CONFIG_FILE="$1"
if [ ! -f "$CONFIG_FILE" ]; then
    echo "Оуп, конфига '$CONFIG_FILE' нет в директории. Проверь путь, не ломай мне тут скрипт."
    exit 1
fi

JAR_PATH="build/libs/pdelab-all.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo "Джарник не найден! Ты хоть собрал проект? Запусти './gradlew shadowJar', блин."
    exit 1
fi

# Пуск в прод (ну почти)
java -jar "$JAR_PATH" run --config "$CONFIG_FILE"
