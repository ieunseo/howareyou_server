
<img width="1920" height="1289" alt="Image" src="https://github.com/user-attachments/assets/774bc1da-4d6e-4d57-8056-bb0173860024" />

# 0. Getting Started (시작하기)

```bash
$ ./gradlew bootRun
$ docker compose up -d

```

서비스 주소: [하우아유](https://howareu.click/)

---

# 1. Project Overview (프로젝트 개요)

* **프로젝트 이름**: HowAreYou (하우아유)
* **프로젝트 설명**:
  외국어 학습(영어-한국어)을 위한 언어교환 플랫폼 입니다.
  다른사람과 대화를 나눠 단어장을 생성하고, 대화와 관심사를 기반으로한 사용자 맞춤 추천, 만들어진 단어장으로 퀴즈를 생성해 학습을해보세요!

---

# 2. Team Members (팀원 및 팀 소개)

|                  BE                  |     BE     |     BE     |     BE     |     BE     |
| :----------------------------------: | :--------: | :--------: | :--------: | :--------: |
|                  엄아영              |     고형준    |     문성원    |     박상화    |     이은서    |
| [GitHub](https://github.com/Eomssi) | [GitHub](https://github.com/taco-recipe) | [GitHub](https://github.com/moonjs1011) | [GitHub](https://github.com/iamsage9346) | [GitHub](https://github.com/ieunseo) |

---

# 3. Key Features (주요 기능)
* **채팅**

  * 실시간 WebSocket 채팅
  * 채팅방별 대화기록 원문/번역본 둘다 저장

* **채팅 기반 단어 추출**

  * NLP 기반 단어 분석 및 추출
  * 개인별 단어장 자동 저장

* **알림 기능**

  * 받는사람이 오프라인인지 확인하는 헬스체크
  * 오프라인인 경우 알림수신

* **맞춤형 퀴즈 생성 & 학습**

  * Daily Quiz (일일 퀴즈)
  * Random Quiz (랜덤 퀴즈)

* **추천 시스템**

  * 사용자의 단어 학습 성향 기반 추천
  * 맞춤형 단어/퀴즈 추천

* **마이페이지 대시보드**

  * 퀴즈 결과 및 정답률 시각화
  * 개인별 학습 현황 관리

---

## 3.1 차별점

| 구분     | HowAreYou                                      | 기존 서비스      |
| ------ | ---------------------------------------------- | ----------- |
| 학습 데이터 | **실시간 채팅 단어 추출**                               | 사전 입력/수동 등록 및 이미 만들어진 데이터 |
| 학습 방식  | **단어장 → 퀴즈 자동 생성**                             | 단어 암기 중심    |
| 학습 피드백 | 퀴즈 결과 기반 **개인화 분석/추천**                         | 단순 정오답 확인   |

---
# 4. Tasks & Responsibilities (작업 및 역할 분담)

| 이름  | 역할 | 주요 작업                                                                                                                                |
| --- | -- | ------------------------------------------------------------------------------------------------------------------------------------ |
| 엄아영 | BE | <ul><li>채팅 → 단어장 생성 파이프라인 구축</li><li>날짜별/전체 단어장 조회 기능</li><li>마이페이지 대시보드</li><li>단어장 부하테스트 진행 및 성능개선</li></ul> 
| 고형준 | BE | <ul><li>로그인 및 jwt spring security 구현</li> <li> 마이페이지 대시보드 구현 </li> <li> 추천알고리즘 구현 </li> <li> 알림기능 구현</li><li>frontend 연동 및 제작</li></ul>
| 문성원 | BE | <ul><li>LiberTranslate + Gemini API 번역 서버 구축</li><li>CICD 구축 및 배포</li><li>FastAPI/Spacy ECS 연동</li><li>k6 부하테스트 진행 및 성능개선</li></ul> 
| 박상화 | BE | <ul><li>WebSocket + Redis 기반 실시간 메시징</li><li>nlp 사용자 태그 벡터 추천 알고리즘 구현</li><li>메시지 저장 비동기처리 (kafka), 사용자 태그 점수 계산 리팩토링</li><li>mongodb 파이프라인 구축</li></ul> 
| 이은서 | BE | <ul><li>퀴즈 생성/제출/채점 로직 구현</li><li>MongoDB + PostgreSQL 데이터 파이프라인</li><li>K6 부하테스트 및 성능 최적화</li></ul> |


---

# 5. Technology Stack (기술 스택)

## 5.1 Language

<img width="50" height="30" alt="Image" src="https://github.com/user-attachments/assets/9f43f57e-9bca-4615-a1d4-79873d7a3f3c"/>java17 / Gradle

## 5.2 Frontend
<img width="80"  alt="React" src="https://shields.io/badge/React-3080CA?logo=React&logoColor=FFF&style=flat-square"/> <img width="120"  alt="TypeScript" src="https://shields.io/badge/TypeScript-3178C6?logo=TypeScript&logoColor=FFF&style=flat-square"/>

## 5.3 Backend

<img width="100" src="https://img.shields.io/badge/SpringBoot-6DB33F?style=flat-square&logo=Spring&logoColor=white"/> <img width="100" src="https://img.shields.io/badge/postgresql-4169E1?style=flat-square&logo=postgresql&logoColor=white"/> <img width="100" src="https://img.shields.io/badge/mongoDB-47A248?style=for-the-badge&logo=MongoDB&logoColor=white"> <img width="80" src="https://img.shields.io/badge/Redis-DC382D?style=for-the-badge&logo=redis&logoColor=white">
<img width="100" height="30" src ="https://img.shields.io/badge/FastAPI-005571?style=for-the-badge&logo=fastapi"/>, <img src="https://img.shields.io/badge/Flask-000000?style=for-the-badge&logo=Flask&logoColor=white"/> <img src="https://img.shields.io/badge/spaCy-green"/> <img width="100"  height="30" src="https://img.shields.io/badge/LibreTranslate-blue"/> <img src="https://img.shields.io/badge/Apache_Kafka-231F20?style=for-the-badge&logo=apache-kafka&logoColor=white"/> <img src="https://img.shields.io/badge/Zookeeper-231F20?style=for-the-badge"/> <img width="100" height="30" src="https://img.shields.io/badge/Prometheus-white?logo=prometheus"/> <img width="100"  height="30" src="https://img.shields.io/badge/-Grafana-000?&logo=Grafana"/> 

## 5.4 Cooperation

<img width="100" alt="Github" src="https://shields.io/badge/github-000000?logo=github&logoColor=FFF&style=flat-square"/>     <img width="100" alt="Discord" src="https://shields.io/badge/discord-3178C6?logo=discord&logoColor=FFF&style=flat-square"/>   <img width="100"  src="https://shields.io/badge/notion-ffffff?logo=notion&logoColor=000000&style=flat-square"/>    

## 5.5 API Test

<img width="100" src="https://img.shields.io/badge/-Swagger-85EA2D?style=flat&logo=swagger&logoColor=white"/> <img width="100" src="https://img.shields.io/badge/Postman-FF6C37?style=for-the-badge&logo=Postman&logoColor=white"/>

## 5.6 Deployment
<img width="3100" height="3900" alt="Image" src="https://github.com/user-attachments/assets/4fdb6d35-9c08-4ff8-b211-e15fa300a57c" />

---


# 6. Project Structure (프로젝트 구조)

```plaintext
server-dev/
├── docs/                     # 문서 가이드
├── fastapi-api/              # NLP 태깅 서버
├── infra/                    # 인프라 설정
├── k6-test/                  # K6 부하테스트
├── spacy-api/                # Spacy 형태소 분석 서버
├── src/                      # Spring Boot 애플리케이션
│   └── main/java/org/example/howareyou/
│       ├── HowAreYouApplication.java
│       ├── domain/
│       │   ├── auth/         # 인증/인가
│       │   ├── chat/         # 채팅
│       │   ├── dashboard/    # 마이페이지/대시보드
│       │   ├── member/       # 회원
│       │   ├── quiz/         # 퀴즈
│       │   └── vocabulary/   # 단어장
│       └── global/           # 공통 설정 및 전역 모듈
│           ├── config/      
│           ├── converter/    # JSON, 데이터 변환기
│           ├── entity/       # 공통 엔티티 (BaseEntity)
│           ├── exception/    # 전역 예외 처리 
│           ├── health/       # 헬스체크 및 상태 모니터링
│           ├── security/     # JWT 인증, Spring Security
│           ├── sse/          # SSE(Server-Sent Events) 처리
│           ├── test/         # 테스트/개발용 컨트롤러
│           └── util/         # 공용 유틸리티 
└── docker-compose.yml        # 서비스 전체 구성

```

---

# 6.1 ERD
<img width="1045" height="833" alt="Image" src="https://github.com/user-attachments/assets/9717a943-792e-40fe-a07d-f56d307b5634" />
---

# 7. Development Workflow (개발 워크플로우)

* Git Flow 전략 적용
* main / dev / feature 브랜치 운영
* PR 기반 코드리뷰 & merge

---

# 8. Commit Convention (커밋컨벤션)


[커밋컨밴션 참고문서](https://www.notion.so/211e51228f4b81cc8f3deced0a87af6d)

---

# 9. Coding Convention (코딩컨벤션)

* DDD 기반 패키지 구조
* DTO / Controller / Service / Repository 네이밍 통일
* 명확한 네이밍 규칙 준수

---

# 10. Code Review

* 모든 PR은 최소 1명 이상의 리뷰 후 merge
* 컨벤션/기능 검증 필수
* 기능 완성될때마다 dev 에 merge

---

# 11. Deployment

1. dev → main merge
2. CI/CD 자동 배포
3. 배포 후 hotfix 브랜치로 긴급 수정

