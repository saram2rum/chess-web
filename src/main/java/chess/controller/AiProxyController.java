package chess.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import chess.dto.EvalResultDTO;
import chess.service.AiEvaluationService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * AI API Gateway: /ai/* 요청을 FastAPI(8000)로 프록시
 * - evaluate: AiEvaluationService 경유 → FEN 캐시 사용 (Single Source of Truth)
 * - 클라이언트/서버 모두 동일 캐시로 동일 값 보장
 */
@RestController
@RequestMapping("/ai")
public class AiProxyController {

    private static final String AI_SERVER_URL = "http://localhost:8000";

    private final RestTemplate restTemplate = new RestTemplate();
    private final AiEvaluationService aiEvaluationService;

    public AiProxyController(AiEvaluationService aiEvaluationService) {
        this.aiEvaluationService = aiEvaluationService;
    }

    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        return proxy(HttpMethod.GET, "/ai/status", null);
    }

    @PostMapping("/get-move")
    public ResponseEntity<String> getMove(@RequestBody String body) {
        return proxy(HttpMethod.POST, "/ai/get-move", body);
    }

    @GetMapping("/evaluate")
    public ResponseEntity<String> evaluate(
            @RequestParam String fen,
            @RequestParam(required = false) Integer searchtime) {
        // AiEvaluationService 경유 → 캐시 hit 시 FastAPI 재호출 없음
        EvalResultDTO eval = aiEvaluationService.evaluate(fen);
        if (eval == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{\"error\":\"Evaluation failed\"}");
        }
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode();
        node.put("type", eval.type());
        node.put("value", eval.value());
        node.put("fen", fen);
        try {
            return ResponseEntity.ok(mapper.writeValueAsString(node));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{}");
        }
    }

    private ResponseEntity<String> proxy(HttpMethod method, String pathOrUrl, String body) {
        String url = pathOrUrl.startsWith("http") ? pathOrUrl : AI_SERVER_URL + pathOrUrl;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }
}
