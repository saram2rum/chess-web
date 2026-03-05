package chess.controller;

import chess.service.MetricLoggingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 메트릭 로깅 on/off API. 부하 테스트 전후에 호출.
 */
@RestController
@RequestMapping("/admin/metrics")
public class MetricController {

    private final MetricLoggingService metricLoggingService;

    public MetricController(MetricLoggingService metricLoggingService) {
        this.metricLoggingService = metricLoggingService;
    }

    @GetMapping("/on")
    public ResponseEntity<String> on() {
        metricLoggingService.setEnabled(true);
        return ResponseEntity.ok("Metric logging started.");
    }

    @GetMapping("/off")
    public ResponseEntity<String> off() {
        metricLoggingService.setEnabled(false);
        return ResponseEntity.ok("Metric logging stopped.");
    }
}
