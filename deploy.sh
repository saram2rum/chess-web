#!/bin/bash

# ═══════════════════════════════════════════════════
# Chessez EC2 배포 스크립트 (Spring + AI 서버)
# ═══════════════════════════════════════════════════
KEY_PATH="${KEY_PATH:-$HOME/Desktop/chess/chess-seoul-key.pem}"
SERVER_IP="${SERVER_IP:-3.36.158.65}"
SERVER_USER="${SERVER_USER:-ubuntu}"
REMOTE_HOME="/home/${SERVER_USER}"

# 1. 프로젝트 빌드 (캐시 완전 제거 후 클린 빌드)
echo "🚧 Building project (clean build, no daemon)..."
./gradlew clean build -x test --no-daemon --refresh-dependencies

if [ $? -ne 0 ]; then
    echo "❌ Build failed."
    exit 1
fi
echo "✅ Build successful!"

# 2. 서버로 전송
echo "🚀 Uploading to server ($SERVER_IP)..."
scp -i "$KEY_PATH" build/libs/chess-spring-0.0.1-SNAPSHOT.jar ${SERVER_USER}@${SERVER_IP}:${REMOTE_HOME}/chess-game.jar

# 3. ai_server 폴더 전송 (venv, __pycache__ 제외)
echo "📦 Uploading ai_server..."
rsync -avz -e "ssh -i $KEY_PATH" \
  --exclude 'venv' \
  --exclude '__pycache__' \
  --exclude '*.pyc' \
  --exclude '.pytest_cache' \
  --exclude '*.egg-info' \
  ai_server/ \
  ${SERVER_USER}@${SERVER_IP}:${REMOTE_HOME}/chess-spring/ai_server/

# 4. 서버에서 실행
echo "🔄 Starting services on server..."
ssh -i "$KEY_PATH" ${SERVER_USER}@${SERVER_IP} << 'ENDSSH'
set -e
REMOTE_HOME="$HOME"

# ① Stockfish, python3-venv, net-tools 설치 (없으면)
echo "📦 Checking dependencies..."
if ! command -v stockfish &>/dev/null; then
    echo "Installing stockfish..."
    sudo apt-get update -qq
    sudo apt-get install -y stockfish
fi
if ! dpkg -l | grep -q python3-venv; then
    echo "Installing python3-venv..."
    sudo apt-get install -y python3-venv python3-pip
fi
if ! command -v netstat &>/dev/null; then
    echo "Installing net-tools (for netstat)..."
    sudo apt-get install -y net-tools
fi

# ② ai_server 가상환경 세팅 및 의존성 설치
AI_DIR="${REMOTE_HOME}/chess-spring/ai_server"
cd "$AI_DIR"

if [ ! -d "venv" ]; then
    echo "Creating venv..."
    python3 -m venv venv
fi
source venv/bin/activate
pip install -q -r requirements.txt

# ③ 기존 AI 서버 종료
echo "Stopping existing AI server..."
pkill -f 'uvicorn main:app' 2>/dev/null || true
sleep 1

# ④ FastAPI 백그라운드 실행 (8000번), 로그는 ai_log.txt에 확실히 기록
AI_LOG="${AI_DIR}/ai_log.txt"
echo "Starting FastAPI (port 8000), log: ${AI_LOG}..."
nohup uvicorn main:app --host 0.0.0.0 --port 8000 >> "${AI_LOG}" 2>&1 &
echo "AI server started (PID: $!), log: ${AI_LOG}"

# ⑤ Spring Boot 종료 후 재시작
echo "Stopping Spring..."
pkill -f 'java -jar chess-game.jar' 2>/dev/null || true
sleep 2

echo "Starting Spring (port 8080)..."
cd "${REMOTE_HOME}"
nohup java -jar chess-game.jar > log.txt 2>&1 &
echo "Spring started (PID: $!)"

echo "🎉 Deployment complete!"
ENDSSH

echo "✨ Deployment finished!"
echo ""
echo "📌 다음 단계:"
echo "   - Nginx: config/nginx/chessez.conf → docs/guides/DEPLOY_GUIDE.md"
echo "   - HTTPS: docs/guides/SSL_HTTPS_GUIDE.md"
