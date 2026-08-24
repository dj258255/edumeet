package com.edu.edumeet.meeting.broadcast;

import com.edu.edumeet.meeting.domain.BroadcastCodecPlan;
import com.edu.edumeet.meeting.domain.ChunkReorderBuffer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 방송 하나의 수명. ffmpeg 프로세스 한 개를 감싼다. (#123)
 *
 * <p>세 가지를 여기서 막는다.
 *
 * <pre>
 *   1. stderr 를 안 읽으면 ffmpeg 가 멈춘다
 *        파이프 버퍼가 차면 ffmpeg 의 쓰기가 블로킹된다. 로그를 안 보겠다고
 *        내버려 두면 "이유 없이 멈춘 방송" 이 된다. 전용 스레드로 계속 읽는다.
 *
 *   2. stdin 쓰기가 HTTP 스레드를 붙잡는다
 *        ffmpeg 가 느리면 write 가 블로킹된다. 요청 스레드에서 직접 쓰면
 *        톰캣 스레드가 묶인다. 유계 큐에 넣고 전용 스레드가 쓴다.
 *
 *   3. 큐가 무한이면 메모리가 터진다
 *        이 프로젝트에서 이미 채팅으로 겪었다. 상한을 두고 넘치면 <b>거부한다.</b>
 *        삼키면 영상이 조용히 깨지고, 거부하면 발표자가 즉시 안다.
 * </pre>
 */
@Slf4j
@Getter
public class BroadcastSession {

    /** 큐에 쌓아 두는 청크 수. 2초 조각이면 8개는 16초 분량이다. */
    private static final int QUEUE_CAPACITY = 8;

    /** 큐가 찼을 때 이만큼만 기다려 본다. 넘으면 거부한다. */
    private static final long OFFER_TIMEOUT_MS = 200;

    private static final byte[] POISON = new byte[0];

    private final Long meetingId;
    private final Process process;
    private final BroadcastCodecPlan codecPlan;
    private final String playlistUrl;
    private final ChunkReorderBuffer reorder;

    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicLong lastChunkAt = new AtomicLong(System.currentTimeMillis());
    private final AtomicLong bytesWritten = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    private final Thread writer;
    private final Thread stderrDrain;

    public BroadcastSession(Long meetingId, Process process, BroadcastCodecPlan codecPlan,
                            String playlistUrl, int reorderWindow) {
        this.meetingId = meetingId;
        this.process = process;
        this.codecPlan = codecPlan;
        this.playlistUrl = playlistUrl;
        this.reorder = new ChunkReorderBuffer(reorderWindow);

        this.writer = daemon("hls-write-" + meetingId, this::pumpToFfmpeg);
        this.stderrDrain = daemon("hls-stderr-" + meetingId, this::drainStderr);
    }

    /**
     * 청크를 받아 순서를 맞춘 뒤 큐에 넣는다.
     *
     * @return 거부했으면 false. 호출자는 이것을 429 로 옮긴다
     */
    public boolean submit(long seq, byte[] data) {
        if (closed.get()) {
            return false;
        }
        lastChunkAt.set(System.currentTimeMillis());

        for (byte[] ordered : reorder.offer(seq, data)) {
            try {
                if (!queue.offer(ordered, OFFER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    rejected.incrementAndGet();
                    log.warn("방송 청크 거부 - meetingId={}, 큐가 찼다. ffmpeg 가 못 따라가고 있다", meetingId);
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /** 마지막 청크로부터 지난 시간(ms). 유령 세션을 걷어내는 데 쓴다. */
    public long idleMillis() {
        return System.currentTimeMillis() - lastChunkAt.get();
    }

    public boolean isAlive() {
        return !closed.get() && process.isAlive();
    }

    public long rejectedCount() {
        return rejected.get();
    }

    public long writtenBytes() {
        return bytesWritten.get();
    }

    /**
     * 방송을 끝낸다. 여러 번 불려도 안전하다.
     *
     * <p><b>stdin 을 닫는 것이 핵심이다.</b> ffmpeg 는 입력이 끝나야 마지막 세그먼트를
     * 마무리하고 플레이리스트를 닫는다. 프로세스를 바로 죽이면 끝부분이 날아간다.
     */
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        queue.clear();          // 남은 것을 굳이 밀어 넣지 않는다. 어차피 끝낸다
        queue.offer(POISON);

        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                log.warn("ffmpeg 가 5초 안에 안 끝났다 - meetingId={}. 강제 종료한다", meetingId);
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
        writer.interrupt();
        stderrDrain.interrupt();

        log.info("방송 종료 - meetingId={}, 보낸 바이트={}, 거부={}, 늦게와서버림={}",
                meetingId, bytesWritten.get(), rejected.get(), reorder.droppedCount());
    }

    /** 데몬으로 띄운다. 방송이 안 끝나도 JVM 종료를 막지 않게. */
    private static Thread daemon(String name, Runnable body) {
        Thread t = new Thread(body, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void pumpToFfmpeg() {
        try (OutputStream stdin = process.getOutputStream()) {
            while (true) {
                byte[] chunk = queue.take();
                if (chunk.length == 0) {        // POISON
                    break;
                }
                stdin.write(chunk);
                stdin.flush();
                bytesWritten.addAndGet(chunk.length);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            // ffmpeg 가 먼저 죽으면 여기로 온다. 세션을 살아 있는 척하게 두지 않는다.
            log.warn("ffmpeg 입력이 끊겼다 - meetingId={}: {}", meetingId, e.getMessage());
            closed.set(true);
        }
    }

    private void drainStderr() {
        try (var reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // ffmpeg 는 진행 상황도 stderr 로 낸다. 전부 남기면 로그가 넘친다.
                if (line.contains("Error") || line.contains("error") || line.contains("Invalid")) {
                    log.warn("ffmpeg[{}] {}", meetingId, line);
                }
            }
        } catch (IOException ignored) {
            // 프로세스가 끝나면 스트림이 닫힌다. 정상 종료 경로다
        }
    }
}
