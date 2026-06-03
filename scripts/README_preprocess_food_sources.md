# food seed preprocess

## 목적
- `음식분류_AI_데이터_영양DB.xlsx`를 `ai_food_source` seed 후보로 정리합니다.
- `20260429_가공식품_277074건.xlsx`를 `packaged_food_source` seed 후보와 exact index alias 후보로 정리합니다.

## 출력 파일
- `src/main/resources/seed/preprocessed/ai_food_entry_seed.csv`
- `src/main/resources/seed/preprocessed/ai_food_entry_alias_seed.csv`
- `src/main/resources/seed/preprocessed/packaged_food_seed.csv`
- `src/main/resources/seed/preprocessed/packaged_food_alias_seed.csv`
- `src/main/resources/seed/preprocessed/preprocess_report.md`

## 실행
```powershell
Set-Location "C:\Users\root\Desktop\github\KSU_Finalproject"
python -u scripts\preprocess_food_sources.py
```

## 테스트
```powershell
Set-Location "C:\Users\root\Desktop\github\KSU_Finalproject\scripts"
python -u -m unittest test_preprocess_food_sources.py
```

## 설계 원칙
- 원본 식품명은 보존합니다.
- exact 검색용으로는 `normalized_*`와 `canonical_*`를 별도로 생성합니다.
- 가공식품은 브랜드/마케팅 토큰을 제거하되, 맛/재료/형태 토큰은 최대한 유지합니다.
- 대표식품명이 너무 generic 하면 exact 우선순위를 낮게 둡니다.


