# 메트릭 로깅 설정 확인 가이드

## 생성된 파일

| 파일 | 역할 |
|------|------|
| `MetricLoggingService` | 1초마다 CPU(%), 메모리(MB) 수집 → CSV 로그 |
| `MetricController` | GET /admin/metrics/on, /admin/metrics/off |
| `logback-spring.xml` | 메트릭 로그 → `logs/metrics.csv` 파일 저장 |
| `application.properties` | `metrics.log.path=logs/metrics.csv` |

## 확인할 두 가지

### 1. EC2 경로 확인

EC2에서 JAR 실행 시 작업 디렉터리가 `/home/ubuntu`이면:
- `logs/metrics.csv` → `/home/ubuntu/logs/metrics.csv`

다른 경로를 쓰려면 실행 시:
```bash
java -jar chess-game.jar --metrics.log.path=/home/ubuntu/logs/metrics.csv
```
또는 `application.properties`에 해당 경로 추가.

### 2. 동작 테스트

1. 서버 실행
2. `http://[서버IP]:8080/admin/metrics/on` 호출 (브라우저 또는 curl)
3. 터미널에서: `tail -f logs/metrics.csv`
4. 1초마다 `METRIC_CSV,...` 라인이 출력되면 성공
5. `http://[서버IP]:8080/admin/metrics/off` 호출 시 중단
