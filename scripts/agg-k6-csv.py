# k6 CSV 표본을 밖에서 집계한다.
#
# 왜 요약을 안 쓰나 - 구독자를 1KB/s 로 조이면 close 프레임이 밀린 데이터 뒤에 갇혀
# 소켓이 안 닫히고, k6 가 모든 시나리오를 끝내고도 종료를 못 한다.
# 종료를 못 하면 handleSummary 도 안 돌아 요약 파일이 안 나온다.
#
#   k6 run --out csv=raw.csv ... ; python3 scripts/agg-k6-csv.py raw-before.csv raw-after.csv

import csv, os, sys, math
from collections import defaultdict
def q(v,p):
    v=sorted(v); k=(len(v)-1)*p; f=math.floor(k); c=math.ceil(k)
    return v[f] if f==c else v[f]*(c-k)+v[c]*(k-f)
for path in sys.argv[1:]:
    vals=defaultdict(list); cnt=defaultdict(float)
    for r in csv.DictReader(open(path)):
        n,v=r['metric_name'],float(r['metric_value'])
        if n in ('caption_ingest_ms','rest_probe_ms'): vals[n].append(v)
        elif n in ('caption_sent','caption_failed'): cnt[n]+=v
    print(f'== {path} ==')
    for n in ('rest_probe_ms','caption_ingest_ms'):
        v=vals[n]
        print(f'  {n:18s} n={len(v):5d} avg={sum(v)/len(v):8.2f} p50={q(v,.5):8.2f} '
              f'p95={q(v,.95):9.2f} p99={q(v,.99):9.2f} max={max(v):9.2f}')
    # 500ms 를 넘긴 프로브가 몇 건인가. 꼬리는 분위수보다 건수로 보는 게 정확하다.
    slow=[x for x in vals['rest_probe_ms'] if x>500]
    print(f'  프로브 500ms 초과: {len(slow)} / {len(vals["rest_probe_ms"])}건'
          f'  ({len(slow)/len(vals["rest_probe_ms"])*100:.2f}%)')
    target = float(os.environ.get('TARGET', '4050'))
    print(f'  자막 전송 {cnt["caption_sent"]:.0f}/{target:.0f} '
          f'({cnt["caption_sent"]/target*100:.1f}%) · 실패 {cnt["caption_failed"]:.0f}')
