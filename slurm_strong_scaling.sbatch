#!/bin/bash
#SBATCH --job-name=pdelab_scale
#SBATCH --output=logs/slurm_strong_scaling_%j.log
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=64
#SBATCH --time=02:00:00
#SBATCH --partition=compute

# Зачищаем мусор и грузим православную 21-ю джаву
module purge
module load java/21

echo "Стартуем strong scaling study (ща будем греть кластер по-взрослому)"
mkdir -p artifacts/scaling

# Зафиксили размеры проблемы (матан не простит ошибок)
N=1024
dt=0.001

for THREADS in 1 2 4 8 16 32 64; do
    
    cat <<EOF > tmp_config_${THREADS}.json
{
  "Nx": $N,
  "Ny": $N,
  "Lx": 1.0,
  "Ly": 1.0,
  "alpha": 0.1,
  "T": 0.05,
  "dt": $dt,
  "scheme": "CN",
  "maxIters": 2000,
  "tol": 1e-9,
  "threads": $THREADS,
  "outDir": "artifacts/scaling",
  "testCase": "NON_TRIVIAL"
}
EOF

    echo "Погнали тестить N=$N, потоки=$THREADS (держитесь, ядра, мы взлетаем)"
    java -Xmx32G -XX:+UseG1GC -XX:ActiveProcessorCount=$THREADS -jar build/libs/pdelab-all.jar run --config tmp_config_${THREADS}.json
done

# Прибираем за собой json-франкенштейнов
rm tmp_config_*.json
echo "Скейлинг отработал без эксепшенов, идем пить смузи"
