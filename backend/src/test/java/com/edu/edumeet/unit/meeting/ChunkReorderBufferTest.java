package com.edu.edumeet.unit.meeting;

import com.edu.edumeet.meeting.domain.ChunkReorderBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 뒤바뀐 청크를 되돌린다. (#123)
 *
 * <p><b>왜 이걸 따로 시험하나.</b> 순서가 틀리면 ffmpeg 가 에러를 내지 않는다.
 * <b>깨진 영상이 나올 뿐이다.</b> 눈으로 보기 전에는 모르고, 눈으로 봐도 원인을 모른다.
 */
@DisplayName("청크 순서 되돌리기")
class ChunkReorderBufferTest {

    private static byte[] b(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String join(List<byte[]> chunks) {
        StringBuilder sb = new StringBuilder();
        chunks.forEach(c -> sb.append(new String(c, StandardCharsets.UTF_8)));
        return sb.toString();
    }

    @Test
    @DisplayName("순서대로 오면 그대로 흘려보낸다")
    void in_order_passes_through() {
        ChunkReorderBuffer buffer = new ChunkReorderBuffer(4);

        assertThat(join(buffer.offer(0, b("A")))).isEqualTo("A");
        assertThat(join(buffer.offer(1, b("B")))).isEqualTo("B");
        assertThat(buffer.pendingCount()).isZero();
    }

    @Test
    @DisplayName("★ 3번이 2번보다 먼저 와도 2·3 순서로 나간다")
    void out_of_order_is_restored() {
        ChunkReorderBuffer buffer = new ChunkReorderBuffer(4);
        buffer.offer(0, b("A"));
        buffer.offer(1, b("B"));

        // 3번이 먼저 도착했다. 아직 아무것도 못 내보낸다.
        assertThat(buffer.offer(3, b("D"))).isEmpty();
        assertThat(buffer.pendingCount()).isEqualTo(1);

        // 2번이 오는 순간 2와 3이 한꺼번에 순서대로 풀린다.
        List<byte[]> released = buffer.offer(2, b("C"));
        assertThat(join(released)).isEqualTo("CD");
        assertThat(buffer.pendingCount()).isZero();
    }

    @Test
    @DisplayName("★ 여러 개가 뒤엉켜도 결과는 항상 원래 순서다")
    void arbitrary_shuffle_still_orders() {
        ChunkReorderBuffer buffer = new ChunkReorderBuffer(8);
        List<byte[]> out = new ArrayList<>();

        for (long seq : new long[]{2, 0, 3, 1, 5, 4}) {
            out.addAll(buffer.offer(seq, b(String.valueOf(seq))));
        }
        assertThat(join(out)).isEqualTo("012345");
    }

    @Test
    @DisplayName("★ 이미 지나간 번호는 버린다 - 다시 쓰면 스트림이 겹쳐 깨진다")
    void late_duplicates_are_dropped() {
        ChunkReorderBuffer buffer = new ChunkReorderBuffer(4);
        buffer.offer(0, b("A"));
        buffer.offer(1, b("B"));

        assertThat(buffer.offer(0, b("A-다시"))).isEmpty();
        assertThat(buffer.droppedCount()).isEqualTo(1);
        assertThat(buffer.expectedSeq()).isEqualTo(2);
    }

    @Test
    @DisplayName("★ 빈자리가 창을 넘도록 안 메워지면 실패시킨다 - 멈춘 채 살아 있는 것이 끊긴 것보다 나쁘다")
    void a_persistent_gap_fails_fast() {
        ChunkReorderBuffer buffer = new ChunkReorderBuffer(2);
        buffer.offer(0, b("A"));

        buffer.offer(2, b("C"));
        buffer.offer(3, b("D"));

        // 1번이 영영 안 온다. 여기서 기다리면 방송이 멈춘 채 "방송 중" 으로 남는다.
        assertThatThrownBy(() -> buffer.offer(4, b("E")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1");
    }

    @Test
    @DisplayName("창 크기는 1 미만일 수 없다")
    void window_must_be_positive() {
        assertThatThrownBy(() -> new ChunkReorderBuffer(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
