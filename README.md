# 🐾 나비 가계부

삼색 고양이 나비가 지켜보는 Android 가계부 앱 (Kotlin + Jetpack Compose).

## 기능
- **오늘 탭**: 나비가 오늘 지출을 보고 표정과 말풍선으로 반응 (궁금 / 기쁨 / 평온 / 걱정 / 깜짝)
  - 오늘 지출 · 평소 하루 · 이번 달 합계
  - 경고선 막대: 평소(100%) 눈금과 경고선(평소 × 배수)
- **달력 탭**: 월간 달력에 날짜별 지출을 색으로 표시 (평소 이하 / 조금 많이 / 과소비), 카테고리별 비중, 날짜별 내역
- **기록 추가**: 지출/수입, 날짜(지난 날짜도 가능), 금액 빠른 추가(+1천/+5천/+1만/+5만), 카테고리, 메모
- **매일 알림**: 정해진 시간(기본 오후 9:00)에 오늘 기록 여부와 평소 대비 지출을 알려줌. 재부팅해도 유지
- **과소비 경고**: 오늘 지출이 평소의 N배(1.2 / 1.5 / 2배 선택)를 처음 넘는 순간 알림 + 앱 안 메시지 (하루 한 번)

### "평소"의 정의
최근 30일(오늘 제외)의 하루 평균 지출. 기록을 시작한 지 3일이 안 됐으면 비교하지 않고 기록을 모으는 상태로 둡니다.
`logic/Stats.kt`의 `WINDOW_DAYS`, `MIN_HISTORY_DAYS`로 조정할 수 있어요.

## 빌드 방법

### A. GitHub Actions (아무것도 설치 안 해도 됨)
1. 이 폴더를 GitHub 저장소로 push
2. Actions 탭 → **Build APK** 실행이 끝나면 `nabi-ledger-debug-apk` 아티팩트 다운로드
3. 휴대폰에 `app-debug.apk` 설치 (출처를 알 수 없는 앱 허용 필요)

### B. 커맨드라인 (Android Studio 없이)
Android SDK command-line tools 설치 후:
```bash
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # SDK 경로에 맞게
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
JDK 17 이상이 필요합니다.

### C. Android Studio
폴더를 열고 ▶ Run.

## 권한
- 알림 (Android 13+): 첫 실행 때 요청
- 알람 및 리마인더 (Android 12+): 없으면 알림이 몇 분 늦을 수 있어 설정 탭에서 허용 버튼 제공
- 부팅 완료 수신: 재부팅 후 알림 재예약

## 구조
```
data/     Entry, 카테고리, 설정, 저장소(entries.json + SharedPreferences)
logic/    평소 계산·과소비 판정·나비 대사(Stats), 포맷(Fmt)
notify/   알림 채널, 매일 알람 예약(AlarmManager), 리시버
ui/       테마, Canvas로 그린 나비, 홈/달력/설정 화면, 기록 다이얼로그
```
나비 그림은 이미지 파일 없이 Compose Canvas로 그립니다 (`ui/NabiCat.kt`).
기록은 이 기기 안에만 저장됩니다.
