package chess.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Idle 방 정리 스케줄러
 * - 로비: 15분 무활동 → 방 삭제
 * - 1v1 진행 중: 15분 무활동 → 방 삭제
 * - 1v1 종료 후: 5분 무활동 → 방 삭제
 */
@Component
public class RoomCleanupScheduler {

    private final ChessService chessService;

    public RoomCleanupScheduler(ChessService chessService) {
        this.chessService = chessService;
    }

    @Scheduled(fixedRate = 60_000) // 1분마다
    public void cleanupIdleRooms() {
        chessService.checkAndCleanIdleRooms();
    }
}
