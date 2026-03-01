package chess.service;

import chess.dto.TimeUpdateDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 3초마다 진행 중인 게임의 타이머를 브로드캐스트 (탭 전환 시 동기화)
 */
@Component
public class TimerBroadcastScheduler {

    private final ChessService chessService;
    private final SimpMessagingTemplate messagingTemplate;

    public TimerBroadcastScheduler(ChessService chessService, SimpMessagingTemplate messagingTemplate) {
        this.chessService = chessService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 3000)  // 3초마다
    public void broadcastTimerUpdates() {
        Map<String, TimeUpdateDTO> rooms = chessService.getActiveRoomsTimerData();
        for (Map.Entry<String, TimeUpdateDTO> e : rooms.entrySet()) {
            messagingTemplate.convertAndSend("/topic/timer/" + e.getKey(), e.getValue());
        }
    }
}
