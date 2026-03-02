package chess.controller;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * 로컬 개발용: /ai/* 요청을 FastAPI(8000)로 프록시
 * - 프로덕션에서는 Nginx가 /ai/*를 8000으로 라우팅하므로 이 컨트롤러는 호출되지 않음
 * - 로컬(localhost:8080)에서는 Nginx 없이 Spring이 /ai/*를 받아 FastAPI로 전달
 */
@RestController
@RequestMapping("/ai")
public class AiProxyController {

    private static final String AI_SERVER_URL = "http://localhost:8000";

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        return proxy(HttpMethod.GET, "/ai/status", null);
    }

    @PostMapping("/get-move")
    public ResponseEntity<String> getMove(@RequestBody String body) {
        return proxy(HttpMethod.POST, "/ai/get-move", body);
    }

    private ResponseEntity<String> proxy(HttpMethod method, String path, String body) {
        String url = AI_SERVER_URL + path;
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
