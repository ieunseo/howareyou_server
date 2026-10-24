package org.example.howareyou.domain.translate.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.howareyou.domain.translate.dto.TranslateRequestDto;
import org.example.howareyou.domain.translate.dto.TranslateResponseDto;
import org.example.howareyou.global.exception.CustomException;
import org.example.howareyou.global.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * 간소화된 Gemini 번역 서비스.
 * - 강력한 context(prompt)만 넣고, 응답 튜닝 로직은 제거.
 * - 가능한 가장 직접적인 번역 문자열을 그대로 반환하되, 앞뒤 공백/따옴표만 정리.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeminiTranslateService {

    @Value("${gemini.base-url}")
    private String baseUrl;

    @Value("${gemini.model}")
    private String model; // ex: "gemini-1.5" or "gemini-2.5-flash"

    @Value("${gemini.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate;

    /**
     * Gemini Api를 사용한 번역 기능을 담당하는 메인 메소드 입니다.
     * @param   requestDto  text,source_language,target_language가 포함되어있습니다.
     * @return  responseDto  번연된 text가 리턴됩니다.
     */
    public TranslateResponseDto translate(TranslateRequestDto requestDto) {
        String q = requestDto.getQ();
        String source = requestDto.getSource();
        String target = requestDto.getTarget();

        // 엔드포인트 결정 (flash 모델은 generateContent, 아니면 generateText)
//        String action = model.toLowerCase().contains("flash") ? ":generateContent" : ":generateText";
        String action = ":generateContent";
        String endpoint = String.format("%s/models/%s%s", baseUrl, model, action);
        String url = UriComponentsBuilder.fromHttpUrl(endpoint).toUriString();

        // 강력한 프롬프트 생성
        String prompt = buildPrompt(source, target, q);

        log.info("Gemini API 호출. model={}, url={}", model, url);

        // 요청 구성
        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> payload = Map.of("contents", List.of(content));

        //header 구성
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Goog-Api-Key", apiKey);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(payload, headers);

        String translated;
        try {
            //restTemplate을 사용한 요청
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            translated = extractSimpleTranslation(response);
        } catch (Exception e) {
            log.error("Gemini 호출 실패", e);
            throw new CustomException(ErrorCode.GM_SERVICE_UNAVAILABLE);
        }

        // 기본 정리: 따옴표 제거, 트림
        translated = cleanBasic(translated);

        TranslateResponseDto dto = new TranslateResponseDto();
        dto.setTranslatedText(translated);
        return dto;
    }

    /**
    * 강력한 프롬포트를 생성합나다.
    * @param sourceLang :   입력으로 들어온 text의 언어 입니다.
    * @param targetLang     출력하고 하는 text의 언어 입니다.
    * @param text           입력으로 들어오는 문자열입니다.
    * @return prompt        강력한 프롬포트를 리턴합니다.
    */
    private String buildPrompt(String sourceLang, String targetLang, String text) {
        return String.join("\n",
                String.format("You are a translation assistant. Translate each line from %s to %s.", capitalize(sourceLang), capitalize(targetLang)),
                "Output one translated line per input line, in the same order.",
                "Do NOT add explanations, comments, examples, transliterations, or extra sentences.",
                "Only output the translation text. Nothing else.",
                String.format("Text:\n\"\"\"\n%s\n\"\"\"", escapeTripleQuotes(text))
        );
    }
    /**
     * 첫 글자만 대문자로 바꾸고 나머지는 소문자로 만들어서 깔끔한 형태로 정렬합니다.
     * @param s 입력 string
     * @return s 첫 글자만 대문자이고 나머지는 모두 소문자인 string으로 반환됩니다.
     */
    private String capitalize(String s) {
        if (s == null || s.isBlank()) return s;
        String lower = s.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
    /**
     * 불 필요한 문자들을 제거 합니다. ex) / \ | ""...
     * @param s 입력 문자열 입니다.
     * @return s 불 필요한 문자들을 제거한 문자열이 반환됩니다.
     */
    private String escapeTripleQuotes(String s) {
        if (s == null) return "";
        return s.replace("\"\"\"", "\\\\\"\\\\\"\\\\\"");
    }
    /**
     * Gemini Api 응답에서 필요한 text를 추출합니다.
     * @param response Gemini Api Response
     * @return target language로 변환된 text
     */
    @SuppressWarnings("unchecked")
    private String extractSimpleTranslation(ResponseEntity<Map> response) {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new CustomException(ErrorCode.GM_PARSE_FAILURE);
        }
        Map<String, Object> body = response.getBody();

        // 1. candidates[0].content.parts[0].text
        if (body.get("candidates") instanceof List<?> candidatesList && !candidatesList.isEmpty()) {
            Object firstCandidate = candidatesList.get(0);
            if (firstCandidate instanceof Map<?, ?> candMap) {
                Object content = ((Map<?, ?>) candMap).get("content");
                if (content instanceof Map<?, ?> contentMap) {
                    Object parts = contentMap.get("parts");
                    if (parts instanceof List<?> partsList && !partsList.isEmpty()) {
                        Object firstPart = partsList.get(0);
                        if (firstPart instanceof Map<?, ?> partMap) {
                            Object textObj = partMap.get("text");
                            if (textObj instanceof String s) {
                                return s;
                            }
                        }
                    }
                }
                // fallback output
                Object outputObj = ((Map<?, ?>) firstCandidate).get("output");
                if (outputObj instanceof String s) return s;
            }
        }

        // 2. outputs[0].content
        if (body.get("outputs") instanceof List<?> outputsList && !outputsList.isEmpty()) {
            Object first = outputsList.get(0);
            if (first instanceof Map<?, ?> outMap) {
                Object content = ((Map<?, ?>) outMap).get("content");
                if (content instanceof String s) return s;
            }
        }

        // 3. choices[0].text
        if (body.get("choices") instanceof List<?> choicesList && !choicesList.isEmpty()) {
            Object first = choicesList.get(0);
            if (first instanceof Map<?, ?> choiceMap) {
                Object text = ((Map<?, ?>) choiceMap).get("text");
                if (text instanceof String s) return s;
            }
        }

        // 마지막 fallback: 전체 body 문자열
        return body.toString();
    }
    /*
    * 받은 문자열의 앞뒤 공백을 제거하고, 양끝에 감싸진 따옴표 (" 또는 “ ”)를 제거합니다.
     */
    private String cleanBasic(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("“") && s.endsWith("”"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }
}