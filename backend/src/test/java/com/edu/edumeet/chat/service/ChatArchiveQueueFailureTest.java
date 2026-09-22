package com.edu.edumeet.chat.service;

import com.edu.edumeet.chat.repository.ChatMessageRepository;
import com.edu.edumeet.meeting.domain.Meeting;
import com.edu.edumeet.meeting.repository.MeetingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("다시보기 채팅 저장 실패 지표")
class ChatArchiveQueueFailureTest {

    @Test
    @DisplayName("★ DB 저장 실패로 배치를 버리면 persist.failed 만 버린 건수만큼 오른다")
    void failed_persist_increments_failure_counter_only() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        when(meetingRepository.findById(anyLong()))
                .thenReturn(Optional.of(Meeting.builder().build()));
        when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        when(chatMessageRepository.saveAll(any()))
                .thenThrow(new RuntimeException("DB failure"));
        ChatArchiveQueue queue = new ChatArchiveQueue(
                meetingRepository, chatMessageRepository, transactionManager, registry, true);

        offer(queue, 3);
        queue.flush();

        assertThat(counter(registry, "chat.archive.persist.failed")).isEqualTo(3);
        assertThat(counter(registry, "chat.archive.dropped")).isZero();
        assertThat(counter(registry, "chat.archive.persisted")).isZero();
    }

    @Test
    @DisplayName("★ 정상 저장이면 persist.failed 는 오르지 않고 저장 건수만 오른다")
    void successful_persist_increments_persisted_only() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        when(meetingRepository.findById(anyLong()))
                .thenReturn(Optional.of(Meeting.builder().build()));
        when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        when(chatMessageRepository.saveAll(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ChatArchiveQueue queue = new ChatArchiveQueue(
                meetingRepository, chatMessageRepository, transactionManager, registry, true);

        offer(queue, 3);
        queue.flush();

        assertThat(counter(registry, "chat.archive.persist.failed")).isZero();
        assertThat(counter(registry, "chat.archive.persisted")).isEqualTo(3);
    }

    @Test
    @DisplayName("★ 한 배치는 트랜잭션을 한 번 열고 실패하면 롤백한다")
    void one_flush_uses_one_transaction_and_rolls_back_on_failure() {
        MeetingRepository meetingRepository = mock(MeetingRepository.class);
        ChatMessageRepository chatMessageRepository = mock(ChatMessageRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        MeterRegistry registry = new SimpleMeterRegistry();
        when(meetingRepository.findById(anyLong()))
                .thenReturn(Optional.of(Meeting.builder().build()));
        when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        when(chatMessageRepository.saveAll(any()))
                .thenThrow(new RuntimeException("DB failure"));
        ChatArchiveQueue queue = new ChatArchiveQueue(
                meetingRepository, chatMessageRepository, transactionManager, registry, true);

        offer(queue, 2);
        queue.flush();

        verify(transactionManager, times(1)).getTransaction(any());
        verify(transactionManager, times(1)).rollback(any());
    }

    private static void offer(ChatArchiveQueue queue, int count) {
        for (int i = 0; i < count; i++) {
            assertThat(queue.offer(1L, "viewer@test", "message-" + i, (long) i)).isTrue();
        }
    }

    private static double counter(MeterRegistry registry, String name) {
        var counter = registry.find(name).counter();
        return counter == null ? 0 : counter.count();
    }
}
