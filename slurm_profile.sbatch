#!/bin/bash
#SBATCH --job-name=pdelab_profile
#SBATCH --output=logs/slurm_profile_%j.log
#SBATCH --nodes=1
#SBATCH --ntasks=1
#SBATCH --cpus-per-task=16
#SBATCH --time=00:30:00
#SBATCH --partition=compute

# Зачищаем энвайронмент от мусора прошлого века
module purge
module load java/21

# Подрубаем JFR чтобы попалить аллокации и посмотреть, кто жрет память
java -XX:StartFlightRecording=duration=60s,filename=profile_jfr.jfr,settings=profile \
     -XX:+UseG1GC \
     -Xmx16G \
     -jar build/libs/pdelab-all.jar run --config configs/profile_config.json

# Если async-profiler на борту, натравливаем его (хардкорный режим):
# ./asprof -d 30 -f artifacts/profile_cpu.html -i 1ms PDE_LAB_PID 
