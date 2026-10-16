#!/system/bin/sh
# usage: run_eval.sh <model> <bs> <ac-mode: fit|full> <out.tsv>
# one whisper-cli run per clip; writes name<TAB>load_ms<TAB>total_ms<TAB>text. Resumes: clips already in out are skipped.
cd /data/local/tmp/stt
M=$1; BS=$2; MODE=$3; OUT=$4; EXTRA=$5
touch $OUT
while read name ac; do
  grep -q "^$name	" $OUT && continue
  mt=$((ac * 6 / 50 + 8))
  [ "$MODE" = full ] && ac=0
  if [ "$MODE" = fitcap ]; then ac=$((ac + 100)); else mt=0; fi
  TXT=$(./whisper-cli -m $M -f ev/$name -l ko -t 4 -bs $BS -ac $ac -mt $mt $EXTRA -nt 2>err.log | tr '\n' ' ' | sed 's/^ *//; s/ *$//')
  L=$(grep "load time" err.log | sed -E 's/.*= *([0-9.]+) ms.*/\1/')
  T=$(grep "total time" err.log | sed -E 's/.*= *([0-9.]+) ms.*/\1/')
  printf '%s\t%s\t%s\t%s\n' "$name" "$L" "$T" "$TXT" >> $OUT
done < ev/list.txt
echo done >> $OUT.done
