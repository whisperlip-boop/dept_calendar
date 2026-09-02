# dept_calendar

Confluence Server/DC 플러그인. Atlassian이 유료화한 Team Calendars를 대체하는 부서 캘린더.

- 타깃: Confluence **7.12.3** (`pom.xml`의 `confluence.version`)
- 개발/테스트: WSL2 docker의 Confluence **7.8.1** — http://localhost:18090
- Atlassian SDK 8.2.7 / AMPS 8.1.2, 빌드는 호스트 JDK 8

## 빌드 & 배포

`atlas-run`은 쓰지 않는다. 항상 빌드 후 기존 docker 인스턴스의 UPM에 REST 업로드한다.

```bash
/opt/atlassian-plugin-sdk/bin/atlas-mvn clean package -o     # -o: 오프라인(빠름)

CONF_URL="http://localhost:18090"; AUTH="<user>:<pass>"
TOKEN=$(curl -s -D - -u "$AUTH" -o /dev/null "$CONF_URL/rest/plugins/1.0/" \
  | grep -i "^upm-token" | awk '{print $2}' | tr -d '\r')
RESP=$(curl -s -u "$AUTH" -X POST -H "X-Atlassian-Token: nocheck" \
  -F "plugin=@target/dept_calendar-1.0.0.jar" "$CONF_URL/rest/plugins/1.0/?token=${TOKEN}")
# RESP는 JSON이 아니라 <textarea>{...}</textarea>로 감싸여 온다. 태그를 벗겨야 파싱된다.
# 설치는 비동기 — RESP의 links.self(pending task)를 폴링한다. 완료 신호는 404가 아니라
# 보통 303이다(둘 다 "더 이상 pending 아님"으로 취급할 것).
# 폴링 없이 바로 요청하면 이전 버전이 응답하거나 REST가 잠깐 404가 난다.

curl -s -u "$AUTH" "$CONF_URL/rest/dept-calendar/1.0/ping"    # {"status":"ok"}
```

JS는 배포 전 문법 검사 가능: `echo | /usr/lib/jvm/java-8-openjdk-amd64/bin/jjs src/main/resources/js/dept_calendar.js`
(`AJS is not defined`만 나오면 문법은 정상.)

Confluence 의존성이 없는 순수 로직(`RecurrenceExpander` 등)은 `target/classes`를 클래스패스에 넣고 단독 실행해 검증할 수 있다.

## 확정된 스코프 결정 (되돌리기 전에 반드시 확인)

- **Jira 연동 — 제외.** Application Link 의존성이 커서 스코프에서 뺐다.
- **외부 캘린더 아웃바운드(CalDAV) — 제외.** 인바운드 iCal 구독만 최후순위로 남겨둠.
- **커스텀 이벤트 타입 — 제외.** 8개 고정.
  아이콘은 `images/event-types/<소문자>.svg` (DEVELOP만 파일명이 `development.svg`).
  **표시 순서는 JS의 `EVENT_TYPES` 배열 하나가 결정**한다(타입 선택 메뉴 + 하단 범례).
  현재 자주 쓰는 순: `WORK DEVELOP TESTING EVENT MEETING LEAVE TRAVEL BIRTHDAY`.
  Java `EventType` enum 순서는 UI와 무관하므로 건드리지 않는다.
- **다중 캘린더 — 제거.** 스페이스당 캘린더 1개를 자동 생성한다(`CalendarService.getOrCreateDefault`,
  기존에 여러 개면 가장 오래된 것 사용). Calendar 엔티티는 내부에만 남아 있고 UI/REST 노출 없음.
  페이지별로 다른 달력을 보여주는 건 매크로의 `eventTypes` 파라미터로 한다.
- **이벤트 공개 범위**: `SPACE`(기본) / `PRIVATE`(작성자 본인만). boolean이 아니라 enum인 이유는
  나중에 그룹·특정 사용자로 넓히기 위해서다.
- **권한 기준점은 스페이스의 "Dept Calendar" 페이지 하나**(`model/CalendarPage.TITLE`).
  매크로가 임베드된 페이지 기준으로 하면 누구나 제한 없는 새 페이지에 매크로를 넣어 우회할 수 있다.
  그 페이지가 아직 없으면 스페이스 권한으로 폴백(`SpaceAccessGuard`).

## 구조

- `ao/` — ActiveObjects 엔티티. `Event.exceptionDates`는 반복 일정에서 건너뛸 `yyyyMMdd` 목록(쉼표 구분).
- `service/RecurrenceRule` — RFC 5545 RRULE 최소 부분집합(`FREQ=WEEKLY;UNTIL=20261231`). iCal 내보내기와 호환되도록 이 포맷 유지.
- `service/RecurrenceExpander` — 회차 전개. **항상 시리즈 시작일 기준 오프셋으로 계산**한다(이전 회차 기준으로 하면
  1/31 → 2/28 → 3/28로 드리프트). 이벤트당 500회차 상한.
- `service/EventOccurrence` — 전개된 가상 회차(DB 행 아님). 반복 없는 이벤트도 회차 1개로 취급.
- `rest/` — `<rest>` 모듈이 이 패키지를 스캔하므로 클래스만 추가하면 등록된다.
- `action/CalendarSpaceAction` — 사이드바 링크 대상. 스페이스에 "Dept Calendar" 페이지를 찾거나 만들고 리다이렉트.
- `macro/CalendarMacro` — `<div class="dept-calendar-app" data-space-key data-view data-event-types>`만 출력하고
  나머지는 JS가 그린다.

## 이 환경에서 실제로 겪은 함정들

- **AO 스키마 마이그레이션은 지연 실행**된다. 플러그인 설치 직후 DB를 보면 새 컬럼이 없다.
  엔티티를 건드리는 REST를 한 번 호출한 뒤에 확인할 것.
- **REST DTO에는 `@XmlRootElement` + `@XmlAccessorType(FIELD)`가 필요하다.** 없으면 Jackson이
  "no properties discovered"로 500을 낸다.
- **`#applyDecorator`를 쓰는 커스텀 Velocity 페이지는 이 인스턴스에서 SiteMesh NPE**
  (`ConfluenceSpaceDecoratorMapper`)를 낸다. 그래서 커스텀 페이지를 포기하고
  "매크로가 박힌 실제 Confluence 페이지로 리다이렉트"하는 방식으로 갔다. 되돌리지 말 것.
- **storage format의 매크로 이름은 모듈 key가 아니라 `<xhtml-macro name=...>`** 이다 (`dept-calendar`).
- **ExceptionMapper는 `.type(APPLICATION_JSON)`을 명시**해야 한다. `@Produces` 없는 DELETE에서
  octet-stream으로 협상돼 깨진다.
- **`.position()`/`getBoundingClientRect()`는 `dialog.show()` 이후에** 호출해야 한다. 숨겨진 상태에서
  호출하면 예외가 나면서 그 아래 핸들러 등록까지 전부 건너뛴다(Add 버튼 무반응의 원인이었음).
- **AUI dropdown2를 aui-dialog2 안에 중첩하지 말 것.** 열릴 때 body로 옮겨지고 다이얼로그가 이를
  바깥 클릭으로 오인해 스스로 닫힌다. 자체 구현 드롭다운(`dept-calendar-eventtype-menu`)을 쓴다.
- **월 그리드는 CSS Grid row-span이 아니라 absolute positioning**으로 그린다. row-span 방식은
  주마다 레인 수가 달라질 때 렌더링이 어긋났다.
- **네이티브 `<input type=date/time>`은 브라우저 로캘을 따른다** (한국어 오전/오후). 그래서 날짜는
  AUI 날짜피커 + `AJS.Meta.get('date.format')`, 시간은 24시간제 텍스트 입력으로 갔다.
- **AUI 폼 스타일은 `form.aui` 안에서만 적용된다.** 페이지 본문에 그냥 놓인 `<select class="select">`는
  AUI가 아니라 브라우저 기본 모양이 된다. 툴바 셀렉트는 그래서 CSS로 같은 박스를 다시 선언했다
  (`.dept-calendar-view-select`). AUI 원래 padding이 `6px 5px 5px`라 select 텍스트가 살짝 아래로
  치우치는데, 대칭 padding으로 덮었다.
- **`Event.who`는 쉼표 구분 사용자명 문자열**이고 자동완성은 displayName이 아니라 **username**을 넣는다.
  달력 막대는 `What (bskim, test)` 형태로 그리는데, 이 괄호 안 값이 곧 who다. 자동완성이 항상
  끝에 `", "`를 붙여서 `" bskim, "` 같은 값이 저장되므로 표시할 땐 trim + 빈 토큰 제거가 필수.
  (`formatWho`) 직접 이메일을 입력한 경우를 위해 `@` 앞부분만 남긴다.
- **UPM의 plugin-icon/plugin-logo는 SVG를 못 받는다.** `<param name="plugin-icon">`에 svg를 주면
  UPM이 `Content-Type: image/png`에 **본문 길이 0**으로 응답해서 기본 이미지가 뜬다(에러는 안 남).
  그래서 `images/calendar.svg`를 PNG(72/144)로 래스터화해서 `pluginIcon.png`/`pluginLogo.png`로 넣는다.
  변환기는 `tools/SvgToPng.java` — 이 환경엔 rsvg/inkscape/ImageMagick이 없어서 Java2D로 직접 그린다
  (아이콘이 `M`/`C`/`Z`와 단색 fill만 쓰므로 가능).
- **이벤트 타입 아이콘은 진한 남색 단색**이다. 색이 들어간 배경 위에 올리면 안 보인다.
  범례 칩을 흰 배경 + 색 스와치로 만든 이유.
- `.field-group` 정렬은 범용 CSS 규칙이 없다. 라벨/입력 배치를 바꿀 땐 **이미 정렬되는 다른 필드와
  동일한 마크업 구조를 그대로 복제**하는 게 유일하게 안정적이었다.
- **AUI 폼의 라벨 컬럼은 `padding-left`(행) + 음수 `margin-left`(라벨) 한 쌍**으로 만들어진다.
  폭을 바꿀 땐 반드시 둘을 같이 바꾼다. 다이얼로그는 `20 + 110 + 250(필드 max-width) + 20 = 400px`
  = `aui-dialog2-small`에 딱 맞춘 상태다. 라벨은 `white-space: nowrap` — 줄바꿈되면 행 높이가
  달라져서 체크박스 정렬이 다시 깨진다.

## 로드맵

완료: 캘린더/이벤트 CRUD, 월·주 뷰(다중일 연속 막대), 매크로 임베드, 사이드바 진입,
반복 일정(회차별 수정/삭제 포함), 이벤트별 공개 범위, 페이지 restrictions 연동, 타입 범례/필터.

남음: 타입 색상 사용자 커스터마이징, 타임라인 뷰, 리마인더 메일, Watch 알림, 인바운드 iCal 구독.
(요구사항이 나오면 착수하기로 함 — 2026-09-02)

## 페이지 restrictions 검증 방법

`test` 계정(비밀번호는 bskim과 동일)으로 2계정 테스트가 된다. 제한 걸기/풀기는 REST로:

```bash
# 페이지 id 조회
curl -s -u "$AUTH" "$CONF_URL/rest/api/content?spaceKey=ds&title=Dept%20Calendar"
# update(편집)만 bskim으로 제한
curl -s -u "$AUTH" -X PUT -H "Content-Type: application/json" -H "X-Atlassian-Token: nocheck" \
  -d '[{"operation":"update","restrictions":{"user":[{"type":"known","username":"bskim"}]}}]' \
  "$CONF_URL/rest/experimental/content/<id>/restriction"
# 해제: user/group을 빈 배열로 PUT
```
