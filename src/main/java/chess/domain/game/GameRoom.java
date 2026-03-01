package chess.domain.game;

import chess.domain.piece.Color;
import chess.dto.GameSettings;

import java.util.Random;

/**
 * 게임 방 정보를 관리하는 클래스
 * ChessGame + 플레이어 세션 정보 + 게임 설정을 함께 관리
 */
public class GameRoom {
    private ChessGame game;  // 게임 시작 전에는 null
    private final String roomId;
    private String hostSessionId;      // 방장
    private String guestSessionId;     // 도전자
    private GameSettings settings;
    private boolean gameStarted;
    
    // 게임 시작 시 확정된 색상
    private Color hostColor;
    private Color guestColor;
    
    // 타이머 (밀리초, 서버 권한)
    private long whiteTimeRemaining;
    private long blackTimeRemaining;
    private long lastMoveTimestamp;  // 현재 턴 시작 시각

    public GameRoom(String roomId) {
        this.roomId = roomId;
        this.settings = GameSettings.defaultSettings();
        this.gameStarted = false;
    }

    /**
     * 플레이어가 방에 입장
     * @param sessionId 플레이어의 세션 ID
     * @return "HOST" 또는 "GUEST"
     */
    public String joinPlayer(String sessionId) {
        if (hostSessionId == null) {
            hostSessionId = sessionId;
            return "HOST";
        }
        if (hostSessionId.equals(sessionId)) {
            return "HOST";
        }
        
        if (guestSessionId == null) {
            guestSessionId = sessionId;
            return "GUEST";
        }
        if (guestSessionId.equals(sessionId)) {
            return "GUEST";
        }
        
        throw new IllegalStateException("Room is full. (2/2)");
    }

    /**
     * 게임 시작 - 색상 할당 및 ChessGame 초기화
     */
    public void startGame() {
        if (guestSessionId == null) {
            throw new IllegalStateException("Opponent has not joined yet.");
        }
        if (gameStarted) {
            throw new IllegalStateException("Game has already started.");
        }
        
        // 색상 할당
        assignColors();
        
        // ChessGame 초기화
        this.game = new ChessGame();
        this.gameStarted = true;
        initTimer();
    }
    
    /** 타이머 초기화 (로비 timeLimit 기준) */
    private void initTimer() {
        int limitMs = Math.max(1, settings.timeLimit()) * 60 * 1000;
        whiteTimeRemaining = limitMs;
        blackTimeRemaining = limitMs;
        lastMoveTimestamp = System.currentTimeMillis();
    }
    
    /**
     * 이동 시 방금 둔 플레이어 시간 차감 + increment 적용
     * @param mover 방금 둔 플레이어
     */
    public void onMoveCompleted(Color mover) {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, now - lastMoveTimestamp);
        
        if (mover == Color.WHITE) {
            whiteTimeRemaining = Math.max(0, whiteTimeRemaining - elapsed);
            whiteTimeRemaining += settings.increment() * 1000L;
        } else {
            blackTimeRemaining = Math.max(0, blackTimeRemaining - elapsed);
            blackTimeRemaining += settings.increment() * 1000L;
        }
        lastMoveTimestamp = now;
    }
    
    /** 브로드캐스트용 현재 시간 (생각하는 동안 elapsed 반영) */
    public long getWhiteTimeForBroadcast(Color currentTurn) {
        if (currentTurn == Color.WHITE) {
            long elapsed = Math.max(0, System.currentTimeMillis() - lastMoveTimestamp);
            return Math.max(0, whiteTimeRemaining - elapsed);
        }
        return whiteTimeRemaining;
    }
    
    public long getBlackTimeForBroadcast(Color currentTurn) {
        if (currentTurn == Color.BLACK) {
            long elapsed = Math.max(0, System.currentTimeMillis() - lastMoveTimestamp);
            return Math.max(0, blackTimeRemaining - elapsed);
        }
        return blackTimeRemaining;
    }

    /**
     * 설정에 따라 색상 할당
     */
    private void assignColors() {
        String startingSide = settings.startingSide();
        
        if ("WHITE".equals(startingSide)) {
            // 방장이 WHITE
            hostColor = Color.WHITE;
            guestColor = Color.BLACK;
        } else if ("BLACK".equals(startingSide)) {
            // 방장이 BLACK
            hostColor = Color.BLACK;
            guestColor = Color.WHITE;
        } else {
            // RANDOM
            Random random = new Random();
            if (random.nextBoolean()) {
                hostColor = Color.WHITE;
                guestColor = Color.BLACK;
            } else {
                hostColor = Color.BLACK;
                guestColor = Color.WHITE;
            }
        }
    }

    /**
     * 해당 세션이 현재 턴의 플레이어인지 확인
     */
    public boolean isCurrentTurnPlayer(String sessionId) {
        if (!gameStarted || game == null) {
            return false;
        }
        
        Color currentTurn = game.getCurrentTurn();
        
        if (sessionId.equals(hostSessionId)) {
            return currentTurn == hostColor;
        }
        if (sessionId.equals(guestSessionId)) {
            return currentTurn == guestColor;
        }
        
        return false;
    }

    /**
     * 세션 ID로 플레이어 색상 조회
     */
    public Color getPlayerColor(String sessionId) {
        if (sessionId.equals(hostSessionId)) {
            return hostColor;
        }
        if (sessionId.equals(guestSessionId)) {
            return guestColor;
        }
        return null;
    }

    public ChessGame getGame() {
        return game;
    }

    public String getRoomId() {
        return roomId;
    }

    public String getHostSessionId() {
        return hostSessionId;
    }

    public String getGuestSessionId() {
        return guestSessionId;
    }

    public GameSettings getSettings() {
        return settings;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public Color getHostColor() {
        return hostColor;
    }

    public Color getGuestColor() {
        return guestColor;
    }

    public boolean isHost(String sessionId) {
        return sessionId.equals(hostSessionId);
    }

    public boolean isFull() {
        return hostSessionId != null && guestSessionId != null;
    }

    /**
     * 게임 설정 업데이트 (방장만, 게임 시작 전)
     */
    public void updateSettings(GameSettings newSettings) {
        this.settings = newSettings;
    }

    /** Play Again 시 게임 리셋 (같은 방에서 새 게임 시작 가능) */
    public void resetForRematch() {
        this.game = null;
        this.gameStarted = false;
    }
    
    public long getWhiteTimeRemaining() { return whiteTimeRemaining; }
    public long getBlackTimeRemaining() { return blackTimeRemaining; }

    /** 시간 패 시 패자 시간 0으로 */
    public void onTimeout(Color loser) {
        if (loser == Color.WHITE) {
            whiteTimeRemaining = 0;
        } else {
            blackTimeRemaining = 0;
        }
    }
}



