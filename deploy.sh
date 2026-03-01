#!/bin/bash

# ═══════════════════════════════════════════════════
# 새 EC2 서버 (서울 리전)
# ═══════════════════════════════════════════════════
KEY_PATH="$HOME/Desktop/chess/chess-seoul-key.pem"  # ← pem 파일명으로 교체
SERVER_IP="3.36.158.65"
SERVER_USER="ubuntu"

# 1. 프로젝트 빌드 (테스트 생략)
echo "🚧 Building project..."
./gradlew clean build -x test

# 2. 빌드 성공 확인
if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
else
    echo "❌ Build failed."
    exit 1
fi

# 3. 서버로 전송 (SCP)
echo "🚀 Uploading to server ($SERVER_IP)..."
scp -i "$KEY_PATH" build/libs/chess-spring-0.0.1-SNAPSHOT.jar ${SERVER_USER}@${SERVER_IP}:~/chess-game.jar

# 4. 서버 재시작 (SSH 접속해서 명령어 실행)
echo "🔄 Restarting server..."
ssh -i "$KEY_PATH" ${SERVER_USER}@${SERVER_IP} << 'EOF'
    # 기존에 돌고 있는 java 프로세스 죽이기
    pkill -f 'java -jar chess-game.jar'
    
    # 잠시 대기
    sleep 2
    
    # 다시 백그라운드로 실행 (로그는 남김)
    nohup java -jar chess-game.jar > log.txt 2>&1 &
    
    echo "🎉 Server restarted!"
EOF

echo "✨ Deployment finished!"
