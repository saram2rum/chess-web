package chess.domain.game;

import chess.domain.piece.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fool's Mate (2수 체크메이트) 수정 검증 테스트
 */
class FoolsMateDebugTest {

    @Test
    @DisplayName("Fool's Mate: 체크메이트가 올바르게 감지되어야 한다")
    void foolsMateShouldBeDetected() {
        ChessGame game = new ChessGame();

        game.move("f2", "f3", null);   // 1. WHITE
        game.move("e7", "e5", null);   // 1. BLACK
        game.move("g2", "g4", null);   // 2. WHITE (앙파상 타겟 g3 생성)
        game.move("d8", "h4", null);   // 2. BLACK → 체크메이트!

        // 검증: 체크 ✓
        assertTrue(game.getBoard().isChecked(Color.WHITE),
                "WHITE 킹은 체크 상태여야 한다");

        // 검증: 체크메이트 ✓
        assertTrue(game.getBoard().isCheckMate(Color.WHITE),
                "WHITE 킹은 체크메이트 상태여야 한다 (앙파상 유령 탈출 수 없음!)");

        // 검증: 게임 종료 ✓
        assertFalse(game.isRunning(),
                "체크메이트 후 게임은 종료되어야 한다");
    }
}
