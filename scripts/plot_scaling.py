#!/usr/bin/env python3
import json
import os
import matplotlib.pyplot as plt
from pathlib import Path

def plot_scaling():
    artifacts_dir = Path("artifacts")
    if not artifacts_dir.exists():
        print("Не найдена папка artifacts. Сначала прогони Strong/Weak скейлинги (а не жди чуда с неба).")
        return

    threads = []
    times = []

    # Греппаем артефакты, вытаскиваем метрики
    for run_dir in artifacts_dir.iterdir():
        if run_dir.is_dir() and (run_dir / "metrics.json").exists():
            with open(run_dir / "metrics.json") as f:
                metrics = json.load(f)
            
            # Достаем инфу по потокам, если она там выжила
            if "env" in metrics and "availableProcessors" in metrics["env"]:
                t = metrics["env"]["availableProcessors"]
                if "totalWallTimeMs" in metrics:
                    time_ms = metrics["totalWallTimeMs"]
                    threads.append(t)
                    times.append(time_ms)

    if not threads:
        print("В артефактах нет скейлинг-метрик. Прогон точно не упал с OOM?")
        return

    # Сортируем и рисуем
    data = sorted(zip(threads, times))
    x, y = zip(*data)

    plt.figure(figsize=(8, 6))
    plt.plot(x, y, marker='o', linestyle='-', color='b')
    plt.title('PDE-LAB Разгон (Strong Scaling)')
    plt.xlabel('Потоки (Ядра)')
    plt.ylabel('Стена (ms, время исполнения)')
    plt.grid(True)
    plt.savefig('scaling_plot.png')
    print("Сохранили scaling_plot.png - скинь архитектору на проверку")

if __name__ == "__main__":
    plot_scaling()
