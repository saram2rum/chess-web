package chess.service;

import chess.dto.EvalResultDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single Source of Truth: 서버에서 Stockfish를 딱 한 번만 호출.
 * - FEN 캐시: 이미 계산된 FEN은 재계산하지 않음 (LRU eviction)
 * - move 시점에 ChessService가 호출 → 결과를 WebSocket으로 방 전체에 브로드캐스트
 */
@Service
public class AiEvaluationService {

    private static final String AI_SERVER_URL = "http://localhost:8000";
    private static final int EVAL_SEARCHTIME_MS = 500;
    private static final int CACHE_MAX_SIZE = 2000;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** LRU 캐시: accessOrder=true → get 시 사용 시각 갱신, 초과 시 가장 오래 사용 안 된 항목 제거 */
    private final Map<String, EvalResultDTO> cache = Collections.synchronizedMap(
            new LinkedHashMap<String, EvalResultDTO>((int) (CACHE_MAX_SIZE / 0.75f) + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, EvalResultDTO> eldest) {
                    return size() > CACHE_MAX_SIZE;
                }
            });

    /**
     * FEN에 대한 형세 평가. 캐시 hit 시 즉시 반환, miss 시 FastAPI 호출 후 캐싱.
     *
     * @param fen 보드 FEN
     * @return 형세 결과 (cp/mate), 실패 시 null
     */
    public EvalResultDTO evaluate(String fen) {
        if (fen == null || fen.isBlank()) return null;

        EvalResultDTO cached = cache.get(fen);
        if (cached != null) return cached;

        try {
            String url = UriComponentsBuilder.fromHttpUrl(AI_SERVER_URL + "/ai/evaluate")
                    .queryParam("fen", fen)
                    .queryParam("searchtime", EVAL_SEARCHTIME_MS)
                    .build()
                    .toUriString();

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, null, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return null;

            JsonNode node = objectMapper.readTree(response.getBody());
            String type = node.has("type") ? node.get("type").asText() : "cp";
            int value = node.has("value") ? node.get("value").asInt() : 0;

            EvalResultDTO result = new EvalResultDTO(type, value);
            // LRU: put 시 removeEldestEntry로 eldest(가장 오래 미사용) 자동 제거
            cache.put(fen, result);
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}
