package com.edu.edumeet.meeting.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 뒤바뀐 청크를 순서대로 되돌린다. (#123)
 *
 * <p><b>왜 필요한가.</b> MediaRecorder 가 만드는 것은 이어 붙여야 뜻이 생기는 <b>하나의 스트림</b>이다.
 * 그런데 청크를 HTTP 로 보내면 순서가 보장되지 않는다 — 브라우저가 연결을 여러 개 쓰면
 * 3번이 2번보다 먼저 도착할 수 있다.
 *
 * <p><b>뒤바뀐 채로 ffmpeg 에 넣으면 에러가 아니라 깨진 영상이 나온다.</b>
 * 그래서 여기서 막는다.
 *
 * <p>버퍼가 가득 차면 <b>기다리지 않고 실패시킨다.</b> 잃어버린 청크를 영원히 기다리면
 * 방송이 멈춘 채로 살아 있게 되는데, 그건 끊긴 것보다 나쁘다 — 발표자는 방송 중인 줄 안다.
 */
public class ChunkReorderBuffer {

    private final int windowSize;
    private final Map<Long, byte[]> pending = new HashMap<>();
    private long expected = 0;
    private long dropped = 0;

    public ChunkReorderBuffer(int windowSize) {
        if (windowSize < 1) {
            throw new IllegalArgumentException("창 크기는 1 이상이어야 한다: " + windowSize);
        }
        this.windowSize = windowSize;
    }

    /**
     * 청크 하나를 넣고, 지금 순서대로 쓸 수 있게 된 것들을 돌려준다.
     *
     * @return 이어서 쓸 청크들. 비어 있을 수 있고 여러 개일 수도 있다
     * @throws IllegalStateException 창을 넘도록 빈자리가 안 메워졌을 때
     */
    public List<byte[]> offer(long seq, byte[] data) {
        if (seq < expected) {
            // 이미 지나간 번호다. 재전송이거나 중복이다. 다시 쓰면 스트림이 겹친다.
            dropped++;
            return List.of();
        }
        if (seq > expected && pending.size() >= windowSize) {
            throw new IllegalStateException(
                    "청크 %d 를 기다리는 동안 %d 개가 밀렸다. 순서를 되돌릴 수 없다"
                            .formatted(expected, pending.size()));
        }

        pending.put(seq, data);

        List<byte[]> ready = new ArrayList<>();
        byte[] next;
        while ((next = pending.remove(expected)) != null) {
            ready.add(next);
            expected++;
        }
        return ready;
    }

    /** 다음에 기다리는 번호. */
    public long expectedSeq() {
        return expected;
    }

    /** 지금 순서를 기다리며 붙들고 있는 개수. */
    public int pendingCount() {
        return pending.size();
    }

    /** 늦게 와서 버린 개수. 0 이 아니면 네트워크나 클라이언트를 의심한다. */
    public long droppedCount() {
        return dropped;
    }
}
