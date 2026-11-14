# 디자인

| 파일 | 역할 |
|---|---|
| [`tokens.json`](tokens.json) | **색·크기·글자 크기의 유일한 원본.** Figma 변수 이름과 같고, 앱 `Theme.kt`(`DS`)와 자동 테스트로 묶여 있음 |
| [`screens.md`](screens.md) | **시연 화면 14개의 유일한 명세.** 화면별 요소·조작·다음 화면·코드 위치 |
| [`figma-prompt.md`](figma-prompt.md) | Figma AI에 붙여 넣을 프롬프트 (screens.md + tokens.json 내용을 한 블록으로 정리) |

- Figma 파일: _여기에 링크 추가_
- 화면 번호(#)는 [시연 대본](../docs/demo-script.md)과 같습니다.

## Figma에서 디자인이 바뀌면

1. Figma에서 수정
2. 색·크기·글자 크기가 바뀌었으면 `tokens.json`, 화면 구성·문구가 바뀌었으면 `screens.md`를 고침 (`figma-prompt.md`도 같은 줄 수정)
3. 테스트 실행 → 앱에서 맞춰야 할 값이 목록으로 나옴

   ```bash
   cd android && ./gradlew -Pdomain.only=true :tools:design-tokens:test
   ```

4. 테스트가 알려준 `Theme.kt` 값, `screens.md`의 "코드 위치" 열에 적힌 화면 코드를 수정
5. 커밋 → GitHub Actions가 다시 확인

## 디자인 원칙

- 차량 대화면, 다크 기본, 큰 글씨(본문 최소 20), 터치 영역 최소 76.
- 주행 화면(#3~#6, #14)에는 텍스트 입력·목록·대화 요소를 두지 않습니다.
- 시뮬레이션 데이터가 쓰인 화면에는 항상 `시뮬레이션` 배지.
- AI가 추론한 내용은 보라색 `AI 추론 · 확인 필요`, 사용자가 말한 내용은 파란색 `내가 말함` 배지.
