package chess.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 부하 테스트 시 CPU/메모리 메트릭을 CSV 형식으로 로깅.
 * /admin/metrics/on 으로 시작, /admin/metrics/off 로 중단.
 * CPU는 /proc/stat 기반 1초 평균 (htop과 동일한 계산 방식).
 */
@Service
public class MetricLoggingService {

    private static final Logger log = LoggerFactory.getLogger(MetricLoggingService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private volatile boolean isEnabled = false;
    private volatile boolean isRunning = false;  // 중복 실행 방지
    private long prevTotal = -1;
    private long prevIdle = -1;

    @Scheduled(fixedDelay = 1000, initialDelay = 1000)  // 이전 실행 종료 후 1초 대기
    public void collectAndLog() {
        if (!isEnabled || isRunning) return;
        isRunning = true;
        try {
            double cpuPercent = readCpuUsageFromProcStat();
            if (cpuPercent < 0) cpuPercent = 0;  // 첫 호출 또는 비-Linux
            var osBean = ManagementFactory.getPlatformMXBean(
                    com.sun.management.OperatingSystemMXBean.class);
            long totalMem = osBean.getTotalPhysicalMemorySize();
            long freeMem = osBean.getFreePhysicalMemorySize();
            long usedMemMB = (totalMem - freeMem) / (1024 * 1024);

            String time = LocalDateTime.now().format(FMT);
            String line = String.format("METRIC_CSV,%s,%.2f,%d", time, cpuPercent, usedMemMB);
            log.info(line);
        } catch (Exception e) {
            log.warn("Metric collection failed: {}", e.getMessage());
        } finally {
            isRunning = false;
        }
    }

    public void setEnabled(boolean enabled) {
        if (enabled && !this.isEnabled) {
            log.info("METRIC_CSV,timestamp,cpu_percent,memory_mb");
        }
        this.isEnabled = enabled;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * /proc/stat 기반 CPU 사용률 (htop과 동일한 1초 구간 평균).
     * Linux 전용. Windows/Mac에서는 -1 반환.
     */
    private double readCpuUsageFromProcStat() {
        try (var reader = new BufferedReader(new FileReader("/proc/stat"))) {
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) return -1;
            String[] parts = line.split("\\s+");
            long user = Long.parseLong(parts[1]);
            long nice = Long.parseLong(parts[2]);
            long system = Long.parseLong(parts[3]);
            long idle = Long.parseLong(parts[4]);
            long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0;
            long irq = parts.length > 6 ? Long.parseLong(parts[6]) : 0;
            long softirq = parts.length > 7 ? Long.parseLong(parts[7]) : 0;
            long steal = parts.length > 8 ? Long.parseLong(parts[8]) : 0;

            long total = user + nice + system + idle + iowait + irq + softirq + steal;
            long idleTotal = idle + iowait;

            if (prevTotal >= 0 && prevIdle >= 0) {
                long deltaTotal = total - prevTotal;
                long deltaIdle = idleTotal - prevIdle;
                if (deltaTotal > 0) {
                    double used = 1.0 - ((double) deltaIdle / deltaTotal);
                    prevTotal = total;
                    prevIdle = idleTotal;
                    return Math.max(0, Math.min(100, used * 100));
                }
            }
            prevTotal = total;
            prevIdle = idleTotal;
            return -1;  // 첫 호출 또는 delta=0
        } catch (IOException | NumberFormatException e) {
            log.debug("Could not read /proc/stat: {}", e.getMessage());
            return -1;
        }
    }
}
