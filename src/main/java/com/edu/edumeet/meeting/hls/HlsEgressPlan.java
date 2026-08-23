package com.edu.edumeet.meeting.hls;

import livekit.LivekitEgress.SegmentedFileOutput;

/**
 * LiveKit 에 보낼 HLS egress 요청 한 건. (#75)
 *
 * <p><b>이 프로젝트에서 HLS 의 함정은 전부 요청에 있다. 응답이 아니다.</b>
 * 라이브 플레이리스트 누락, 디렉터리 불일치, 세그먼트 길이 기본값, 오디오 전용 여부 —
 * 전부 요청을 만드는 시점에 정해지고, 잘못 만들면 <b>에러가 아니라 이상한 방송</b>이 된다.
 *
 * <p>그래서 요청 생성을 값으로 분리했다.
 * 실제 egress 인스턴스 없이도({@code SYS_ADMIN} · Chrome · 4코어가 필요하다)
 * 함정 전부를 테스트로 고정할 수 있다.
 */
public record HlsEgressPlan(
        String roomName,
        SegmentedFileOutput output,
        boolean audioOnly
) {

    /**
     * LiveKit egress 가 이 요청에 매기는 CPU 비용.
     *
     * <p>소스에서 확인한 값이다 ({@code pkg/stats/monitor.go} · {@code pkg/config/service.go}).
     * <pre>
     *   if r.RoomComposite.AudioOnly { costs.cpu = AudioRoomCompositeCpuCost }  // 1
     *   else                        { costs.cpu = RoomCompositeCpuCost }       // 4
     * </pre>
     *
     * <p>왜 4배가 아니라 <b>다른 파이프라인</b>인가 — 오디오 전용이면
     * {@code ShouldUseSDKSource} 경로를 타서 <b>헤드리스 Chrome 합성을 아예 건너뛴다.</b>
     * Chromium · Xvfb · 화면 합성이 통째로 빠진다.
     */
    public double estimatedCpuCost() {
        return audioOnly ? AUDIO_ROOM_COMPOSITE_CPU_COST : ROOM_COMPOSITE_CPU_COST;
    }

    /** {@code pkg/config/service.go} — {@code roomCompositeCpuCost = 4} */
    public static final double ROOM_COMPOSITE_CPU_COST = 4;

    /** {@code pkg/config/service.go} — {@code audioRoomCompositeCpuCost = 1} */
    public static final double AUDIO_ROOM_COMPOSITE_CPU_COST = 1;

    /**
     * 코어가 {@code availableCpu} 개인 egress 인스턴스가 이 요청을 받아 줄 것인가.
     *
     * <p>{@code pkg/stats/monitor.go} 의 판정을 그대로 옮겼다.
     * <pre>
     *   required := costs.cpu
     *   accept   := available &gt;= required          // 아니면 ErrNotEnoughCPU
     * </pre>
     *
     * <p><b>우리 OCI 서버는 2 OCPU 다.</b>
     * 비디오 방송 HLS 는 {@code 2 >= 4} 가 거짓이라 <b>거부된다.</b>
     * 오디오 방송 HLS 는 {@code 2 >= 1} 이라 <b>받아 준다.</b>
     *
     * <p>egress 프로세스 자체는 뜬다. 시작 시점 검사({@code validateCPUConfig})는
     * <b>가장 싼 egress 타입</b>({@code trackCpuCost = 0.5})과만 비교하기 때문이다.
     * 그래서 <b>서버는 정상으로 보이는데 방송 시작만 실패하는</b> 모양이 된다.
     */
    public boolean fitsOn(double availableCpu) {
        return availableCpu >= estimatedCpuCost();
    }
}
