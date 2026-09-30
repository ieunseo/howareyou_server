package org.example.howareyou.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    /*──────────────────────── ErrorCode 작성 요령 ────────────────────────
     * ➡️  형식       :  ENUM_NAME(HttpStatus.XXX, "XNNN", "기본 메시지"),
     * ─────────────────────────────────────────────────────────────────────
     * 1) ENUM_NAME        : 대문자 스네이크 케이스  ― “도메인_원인” 형태로 짧고 명확히
     *                      예) USER_NOT_FOUND, AUTH_TOKEN_EXPIRED
     *
     * 2) HttpStatus.XXX   : 클라이언트에 내려줄 HTTP 상태
     *                      4xx → 사용자 잘못, 5xx → 서버/의존 서비스 오류
     *
     * 3) "XNNN"           : 서비스 내부 식별 코드 (문자 1 + 숫자 3)
     *                      · 첫 글자  : 도메인 구분자  (C=Common, U=User, A=Auth, P=Post …)
     *                      · 뒷 세 자리: 001, 002 …  순차 증가 (각 도메인별 독립 번호)
     *
     * 4) "기본 메시지"     : 사용자에게 보여 줄 기본 한글(또는 i18n 키) 메시지
     *                      · 너무 기술적인 내용은 ❌
     *                      · 예외 상세가 필요할 땐 CustomException detail 필드 사용
     *
     * 📌 새 에러 추가 절차
     *    ① 위 네 가지 규칙에 맞춰 ENUM 상수 한 줄 추가
     *    ② 필요하다면 전용 도메인 Exception 클래스에서 throw
     *    ③ 전역 핸들러(GlobalExceptionHandler)는 자동 처리
     *────────────────────────────────────────────────────────────────────*/

    /* ───────────[공통]─────────── */
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST,   "C001", "입력값이 유효하지 않습니다."),
    METHOD_NOT_ALLOWED   (HttpStatus.METHOD_NOT_ALLOWED,"C002", "허용되지 않은 HTTP 메서드입니다."),
    INTERNAL_SERVER_ERROR  (HttpStatus.INTERNAL_SERVER_ERROR,"C500","서버 오류가 발생했습니다."),


    /* ───────────[인증]─────────── */
    AUTH_BAD_CREDENTIAL        (HttpStatus.UNAUTHORIZED, "A001", "ID 또는 비밀번호가 일치하지 않습니다."),
    AUTH_TOKEN_EXPIRED         (HttpStatus.UNAUTHORIZED, "A002", "토큰이 만료되었습니다."),
    AUTH_INVALID_REFRESH_TOKEN (HttpStatus.UNAUTHORIZED, "A003", "유효하지 않은 리프레시 토큰입니다."),
    AUTH_EXPIRED_REFRESH_TOKEN (HttpStatus.UNAUTHORIZED, "A004", "만료된 리프레시 토큰입니다. 다시 로그인해주세요."),
    AUTH_SOCIAL_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND,  "A005", "연결된 소셜 계정을 찾을 수 없습니다."),
    DUPLICATE_EMAIL            (HttpStatus.CONFLICT,    "A006", "이미 가입된 이메일입니다."),
    AUTH_REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A007", "리프레시 토큰이 존재하지 않습니다."),
    AUTH_LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED,"A008","로그인이 필요합니다"),

    /* ───────────[멤버]─────────── */
    MEMBER_NOT_FOUND       (HttpStatus.NOT_FOUND,   "M001", "해당 사용자를 찾을 수 없습니다."),
    DUPLICATED_MEMBERNAME  (HttpStatus.CONFLICT,    "M002", "이미 사용 중인 닉네임입니다."),

    /* ───────────[프로필]─────────── */
    PROFILE_NOT_FOUND     (HttpStatus.NOT_FOUND,     "P001", "프로필을 찾을 수 없습니다."),
    PROFILE_NOT_COMPLETED (HttpStatus.BAD_REQUEST,   "P002", "프로필 작성을 완료해주세요."),
    INVALID_NICKNAME      (HttpStatus.BAD_REQUEST,   "P003", "유효하지 않은 닉네임입니다."),
    /* ───────────[소셜 로그인]──────── */
    SOCIAL_AUTH_FAILED    (HttpStatus.UNAUTHORIZED,  "S001", "소셜 인증에 실패했습니다."),
    SOCIAL_EMAIL_REQUIRED (HttpStatus.BAD_REQUEST,   "S002", "이메일 제공에 동의해주세요."),
    UNSUPPORTED_PROVIDER  (HttpStatus.BAD_REQUEST,   "S003", "지원하지 않는 OAuth2 공급자입니다."),

    /* ───────────[chat room]─────────── */
    CHAT_ROOM_NOT_FOUND (HttpStatus.NOT_FOUND, "CR001", "채팅방을 찾을 수 없습니다."),
    FORBIDDEN_CHAT_ROOM_ACCESS (HttpStatus.FORBIDDEN, "CR002", "채팅방 접근 권한이 없습니다."),
    INVALID_CHAT_ROOM_UUID (HttpStatus.NOT_FOUND, "CR003", "기존 채팅방의 UUID가 유효하지 않습니다."),
    INVALID_CHAT_ROOM_STATE (HttpStatus.NOT_FOUND, "CR004", "채팅방의 상태가 올바르지 않습니다."),

    /* ───────────[LiberTranslate 서버/번역]─────────── */
    LT_CONNECTION_FAILURE(HttpStatus.BAD_GATEWAY, "LT001", "번역 서버와 연결할 수 없습니다."),
    LT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "LT002", "번역 요청이 시간 내에 완료되지 않았습니다."),
    LT_DNS_FAILURE(HttpStatus.BAD_GATEWAY, "LT003", "번역 서버의 DNS를 해석할 수 없습니다."),
    LT_SSL_FAILURE(HttpStatus.BAD_GATEWAY, "LT004", "SSL/TLS 핸드셰이크 실패로 안전한 연결을 만들 수 없습니다."),
    LT_BAD_REQUEST(HttpStatus.BAD_REQUEST, "LT005", "번역 서버에 잘못된 요청을 보냈습니다."),
    LT_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "LT006", "번역 서버 인증에 실패했습니다."),
    LT_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "LT007", "번역 서버 요청 한도를 초과했습니다."),
    LT_UNEXPECTED_RESPONSE(HttpStatus.BAD_GATEWAY, "LT008", "번역 서버로부터 예상치 못한 응답을 받았습니다."),
    LT_PARSE_FAILURE(HttpStatus.BAD_GATEWAY, "LT009", "번역 서버 응답 파싱에 실패했습니다."),
    LT_INTERNAL_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "LT010", "번역 서버 내부 오류가 발생했습니다."),
    LT_UNKNOWN_PROCESSING_ERROR(HttpStatus.BAD_GATEWAY, "LT011", "번역 처리 중 알 수 없는 오류가 발생했습니다."),
    LT_INVALID_INPUT(HttpStatus.UNPROCESSABLE_ENTITY, "LT012", "번역할 텍스트가 비어 있거나 유효하지 않습니다."),
    LT_EMPTY_TRANSLATION(HttpStatus.BAD_GATEWAY, "LT013", "번역 결과가 비어 있거나 예상된 형식이 아닙니다."),
    LT_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "LT014", "번역 서버가 일시적으로 사용 불가능합니다. 재시도 해주세요."),
    LT_CONNECTION_INTERRUPTED(HttpStatus.BAD_GATEWAY, "LT015", "번역 서버와의 연결이 중간에 끊겼습니다."),
    LT_CONFIG_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "LT016", "번역 서버 주소 구성값이 잘못되었습니다."),
    LT_MISSING_PARAMETER(HttpStatus.INTERNAL_SERVER_ERROR, "LT017", "번역 서버 호출에 필요한 헤더/파라미터가 누락되었습니다."),
    LT_FORBIDDEN(HttpStatus.FORBIDDEN, "LT018", "번역 서버 접근 권한이 없습니다."),
    /* ───────────[GeminiApi 번역]─────────── */
    GM_CONNECTION_FAILURE(HttpStatus.BAD_GATEWAY, "GM001", "Gemini 서버와 연결할 수 없습니다."),
    GM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "GM002", "Gemini 요청이 시간 내에 완료되지 않았습니다."),
    GM_DNS_FAILURE(HttpStatus.BAD_GATEWAY, "GM003", "Gemini 서버의 DNS를 해석할 수 없습니다."),
    GM_SSL_FAILURE(HttpStatus.BAD_GATEWAY, "GM004", "SSL/TLS 핸드셰이크 실패로 안전한 연결을 만들 수 없습니다."),
    GM_BAD_REQUEST(HttpStatus.BAD_REQUEST, "GM005", "Gemini에 잘못된 요청을 보냈습니다."),
    GM_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "GM006", "Gemini 인증에 실패했습니다. API 키가 없거나 유효하지 않습니다."),
    GM_FORBIDDEN(HttpStatus.FORBIDDEN, "GM007", "Gemini 접근 권한이 없습니다."),
    GM_NOT_FOUND(HttpStatus.NOT_FOUND, "GM008", "지정한 모델 또는 엔드포인트를 찾을 수 없습니다."),
    GM_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "GM009", "Gemini 요청 한도를 초과했습니다."),
    GM_INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "GM010", "Gemini로부터 받은 응답이 예상한 형식이 아닙니다."),
    GM_PARSE_FAILURE(HttpStatus.BAD_GATEWAY, "GM011", "Gemini 응답 파싱에 실패했습니다."),
    GM_INTERNAL_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "GM012", "Gemini 내부 오류가 발생했습니다."),
    GM_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "GM013", "Gemini 서비스가 일시적으로 사용 불가능합니다. 재시도하세요."),
    GM_CONNECTION_INTERRUPTED(HttpStatus.BAD_GATEWAY, "GM014", "Gemini 서버와의 연결이 중간에 끊겼습니다."),
    GM_MODEL_MISMATCH(HttpStatus.BAD_REQUEST, "GM015", "지정한 모델이 지원되지 않거나 잘못된 액션 suffix를 사용했습니다."),
    GM_QUOTA_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "GM016", "Gemini 사용량 쿼터를 초과했습니다."),
    GM_AUTH_HEADER_MISSING(HttpStatus.BAD_REQUEST, "GM017", "필수 인증 헤더가 누락되었습니다."),
    GM_UNKNOWN(HttpStatus.BAD_GATEWAY, "GM018", "알 수 없는 Gemini 처리 오류가 발생했습니다."),

    /* ───────────[알림]─────────── */
    NOTIFICATION_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "N001", "알림 발송에 실패했습니다."),
    SSE_CONNECTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "N002", "SSE 연결에 실패했습니다."),
    SSE_EMITTER_NOT_FOUND(HttpStatus.NOT_FOUND, "N003", "SSE 연결을 찾을 수 없습니다."),
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N004", "알림을 찾을 수 없습니다."),
    INVALID_NOTIFICATION_TYPE(HttpStatus.BAD_REQUEST, "N005", "유효하지 않은 알림 타입입니다."),
    NOTIFICATION_RECEIVER_NOT_FOUND(HttpStatus.NOT_FOUND, "N006", "알림 수신자를 찾을 수 없습니다."),

    /* ───────────[단어장]─────────── */
    VOCABULARY_NOT_FOUND(HttpStatus.NOT_FOUND, "V001", "단어장을 찾을 수 없습니다."),

    /* ───────────[Vector]─────────── */
    VECTOR_SERIALIZATION_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "VEC001", "사용자 벡터 직렬화에 실패했습니다."),
    VECTOR_DESERIALIZATION_FAIL(HttpStatus.INTERNAL_SERVER_ERROR, "VEC002", "사용자 벡터 역직렬화에 실패했습니다."),
    /* ───────────[퀴즈]─────────── */
    INVALID_DATE_FORMAT(HttpStatus.BAD_REQUEST, "Q001", "잘못된 날짜 형식입니다. yyyy-dd-MM 형식으로 보내주세요."),
//    INSUFFICIENT_DISTRACTORS(HttpStatus.UNPROCESSABLE_ENTITY, "Q002", "오답 선택지가 부족합니다."),
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, "Q003", "퀴즈를 찾을 수 없습니다."),
    QUIZ_ALREADY_SUBMITTED(HttpStatus.CONFLICT, "Q004", "이미 채점된 퀴즈입니다."),
    INVALID_SELECTION_COUNT(HttpStatus.BAD_REQUEST, "Q005", "문제 개수와 답안 개수가 다릅니다."),
    INVALID_SELECTION_INDEX(HttpStatus.BAD_REQUEST, "Q006", "답안 인덱스가 보기 범위를 벗어났습니다."),
    QUIZ_FORBIDDEN(HttpStatus.FORBIDDEN, "Q007", "해당 퀴즈에 접근 권한이 없습니다."),
    DAILY_VOCAB_NOT_FOUND(HttpStatus.NOT_FOUND, "Q008", "해당 날짜의 단어장을 찾을 수 없습니다."),
    QUIZ_BUILD_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Q009", "퀴즈 생성에 실패했습니다. 단어장을 좀 더 생성한 뒤 다시 시도해주세요."),
    INSUFFICIENT_UNIQUE_WORDS_RANDOM (HttpStatus.UNPROCESSABLE_ENTITY, "Q010", "랜덤 퀴즈 생성에 필요한 유니크 단어 수가 부족합니다."),
    INSUFFICIENT_UNIQUE_WORDS_DAILY  (HttpStatus.UNPROCESSABLE_ENTITY, "Q011", "데일리 퀴즈 생성에 필요한 유니크 단어 수가 부족합니다."),
    INSUFFICIENT_DISTRACTORS_RANDOM  (HttpStatus.UNPROCESSABLE_ENTITY, "Q012", "랜덤 퀴즈의 오답 선택지가 부족합니다."),
    INSUFFICIENT_DISTRACTORS_DAILY   (HttpStatus.UNPROCESSABLE_ENTITY, "Q013", "데일리 퀴즈의 오답 선택지가 부족합니다."),
    EMPTY_GENERATED_SET              (HttpStatus.UNPROCESSABLE_ENTITY, "Q014", "퀴즈 문항이 한 개도 생성되지 않았습니다."),
    QUIZ_CHOICE_POOL_BUILD_FAILED    (HttpStatus.UNPROCESSABLE_ENTITY, "Q015", "퀴즈 보기 풀 구성에 실패했습니다."),

    /* ───────────[대시보드]─────────── */
    DASHBOARD_CALCULATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "D001", "대시보드 데이터 계산 중 오류가 발생했습니다.");


    /* Getter ― 럼북을 안 쓴 예시 */
    /* 필드 정의 */
    private final HttpStatus status;  // HTTP 응답용 상태
    private final String     code;    // 서비스 고유 코드(문자/숫자 혼용 가능)
    private final String     message; // 기본 메시지(한국어 or 영어)

    /* 생성자 */
    ErrorCode(HttpStatus status, String code, String message) {
        this.status  = status;
        this.code    = code;
        this.message = message;
    }

}