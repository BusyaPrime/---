import os
import glob
import json
import pandas as pd
import matplotlib.pyplot as plt

def parse_artifacts(base_dir):
    records = []
    
    # Каждому запуску — своя директория (костыль, но работает)
    run_dirs = glob.glob(os.path.join(base_dir, "run_*"))
    for rdir in run_dirs:
        try:
            with open(os.path.join(rdir, "config.json")) as f:
                cfg = json.load(f)
            with open(os.path.join(rdir, "metrics.json")) as f:
                metrics = json.load(f)
            
            rec = {**cfg, **metrics}
            records.append(rec)
        except Exception as e:
            print(f"Скипаем кривую папку {rdir}: эксепшен {e} (кто-то ручками трогал)")
            
    return pd.DataFrame(records)

def plot_strong_scaling(df, out_file):
    if df.empty:
        return
        
    df = df.sort_values(by="threads")
    base_time = df[df["threads"] == 1]["wallTimeSeconds"].iloc[0]
    
    df["speedup"] = base_time / df["wallTimeSeconds"]
    df["efficiency"] = df["speedup"] / df["threads"]
    
    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    
    # Ускорение (Speedup)
    axes[0].plot(df["threads"], df["speedup"], marker='o', label='Факт (Жизнь)')
    axes[0].plot(df["threads"], df["threads"], 'k--', label='Идеал (Матан)')
    axes[0].set_xlabel("Потоки (Threads)")
    axes[0].set_ylabel("Speedup (Во сколько раз быстрее)")
    axes[0].set_title(f"Strong Scaling (Sp)")
    axes[0].legend()
    axes[0].grid(True)
    
    # Эффективность (шоб ядра не простаивали)
    axes[1].plot(df["threads"], df["efficiency"], marker='s', color='orange')
    axes[1].set_xlabel("Потоки (Threads)")
    axes[1].set_ylabel("Эффективность (КПД)")
    axes[1].set_title(f"Parallel Efficiency (Ep)")
    axes[1].set_ylim([0, 1.1])
    axes[1].grid(True)
    
    plt.tight_layout()
    plt.savefig(out_file, dpi=300)
    print(f"Отрандерили график: {out_file}")

if __name__ == "__main__":
    df = parse_artifacts("artifacts/scaling")
    df.to_csv("artifacts/scaling_summary.csv", index=False)
    print(df[["threads", "wallTimeSeconds", "totalPcgIters", "errorL2"]])
    plot_strong_scaling(df, "artifacts/scaling_plot.png")
