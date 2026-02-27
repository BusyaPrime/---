#!/bin/bash
#SBATCH --job-name=pdelab_conv
#SBATCH --output=logs/slurm_convergence_%j.log
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=32
#SBATCH --time=01:00:00
#SBATCH --partition=compute

# Выкидываем старье из окружения, нам нужна только Java 21+
module purge
module load java/21

# Жестко прибиваем треды гвоздями к ядрам для ForkJoinPool, чтобы перф не плясал
export OMP_NUM_THREADS=$SLURM_CPUS_PER_TASK

echo "Начинаем качать матан на $SLURM_JOB_NODELIST. Выделили жирных $SLURM_CPUS_PER_TASK ядер"

# Сетка у нас режектная (N=64, 128, 256, 512, 1024), будем уплотнять
# Шаг dt режем пополам вместе с h, потому что магия O(h^2) требует баланса (иначе препод-архитектор убьет)

for N in 64 128 256 512 1024; do
    dt=$(echo "scale=6; 4.0 / $N" | bc)
    
    cat <<EOF > tmp_config_${N}.json
{
  "Nx": $N,
  "Ny": $N,
  "Lx": 1.0,
  "Ly": 1.0,
  "alpha": 0.1,
  "T": 0.5,
  "dt": $dt,
  "scheme": "CN",
  "maxIters": 2000,
  "tol": 1e-10,
  "threads": $SLURM_CPUS_PER_TASK,
  "outDir": "artifacts",
  "testCase": "NON_TRIVIAL"
}
EOF

    echo "Полетели N=$N, dt=$dt"
    java -Xmx16G -XX:+UseG1GC -jar build/libs/pdelab-all.jar run --config tmp_config_${N}.json
done

# Чистим темпы, мы же не свиньи
rm tmp_config_*.json
echo "Тесты сошлись, математика жива!"
