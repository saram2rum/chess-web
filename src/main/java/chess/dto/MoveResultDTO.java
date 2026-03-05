package chess.dto;

import java.util.Map;

/**
 * 이동 결과 DTO
 * boardSnapshot: 표시용, 파싱 없이 바로 DOM 반영 (역할 분리·최적화)
 * fen: chess.js 유효수 계산용 (앙파상·캐슬링 정확 반영)
 * evalType/evalValue: 서버에서 Stockfish 1회만 호출 후 브로드캐스트 (Single Source of Truth)
 * moveSequence: eval 순서 보장 (0=초기, 1=첫 수 후, ...)
 */
public record MoveResultDTO(
    String source,
    String target,
    String nextTurn,
    boolean isCheck,
    boolean isCheckMate,
    boolean isDraw,
    boolean isGameOver,
    String message,
    Map<String, String> boardSnapshot,
    String fen,
    boolean isCapture,
    String winner,        // "WHITE" | "BLACK" | "DRAW" (게임 종료 시만)
    String endReason,     // "Checkmate" | "Threefold Repetition" | "50-Move Rule" | "Stalemate" | "Time Out" 등
    Long whiteTime,       // 밀리초 (서버 동기화)
    Long blackTime,       // 밀리초
    String evalType,      // "cp" | "mate" (형세분석, null 가능)
    Integer evalValue,    // centipawn 또는 mate 수 (null 가능)
    Integer moveSequence  // eval 순서 보장 (0=초기, 1=첫 수 후, ...)
) {
}



