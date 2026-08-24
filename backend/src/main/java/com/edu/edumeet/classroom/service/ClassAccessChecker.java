package com.edu.edumeet.classroom.service;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.classroom.repository.ClassMemberRepository;
import com.edu.edumeet.classroom.repository.ClassRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * "이 사람이 이 클래스의 자원을 볼 수 있는가" 를 한 곳에서 판단한다. (#62)
 *
 * <p>이 검사는 원래 {@code MeetingService} 안에 인라인으로 있었다.
 * 그래서 <b>새 엔드포인트를 만들 때마다 복사하거나, 잊거나</b> 둘 중 하나였고
 * 실제로 두 곳에서 잊혔다 —
 * {@code GET /meeting/summary/{classId}} 와 {@code GET /meetingroom/room/{roomName}} 은
 * <b>로그인만 하면 남의 클래스 자원을 읽을 수 있었다.</b>
 *
 * <p><b>왜 403 인가</b> — 기존 코드는 {@code IllegalArgumentException} 을 던졌다.
 * #27 에서 그 예외를 400 으로 매핑했으므로 <b>권한 없음이 "잘못된 요청" 으로 나갔다.</b>
 * 둘은 다르다. 클라이언트가 재시도해도 소용없다는 뜻을 담아야 한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClassAccessChecker {

    private final ClassRepository classRepository;
    private final ClassMemberRepository classMemberRepository;

    /**
     * 클래스 생성자이거나 수강생이어야 통과한다.
     *
     * @throws AccessDeniedException 둘 다 아닐 때
     */
    @Transactional(readOnly = true)
    public void requireMember(Long classId, String email) {
        if (email == null) {
            throw new AccessDeniedException("인증이 필요합니다.");
        }
        ClassRoom classRoom = classRepository.findById(classId)
                .orElseThrow(() -> new AccessDeniedException("접근할 수 없는 클래스입니다."));

        boolean isOwner = classRoom.getMember() != null
                && email.equals(classRoom.getMember().getEmail());
        boolean isStudent = classMemberRepository
                .findByClassRoomIdAndMemberEmail(classId, email).isPresent();

        if (!isOwner && !isStudent) {
            // 존재 여부까지 알려주지 않는다. "없다" 와 "권한 없다" 를 구분해주면
            // 클래스 id 를 훑어서 목록을 만들 수 있다.
            log.warn("클래스 접근 거부 - classId={}, email={}", classId, email);
            throw new AccessDeniedException("이 클래스의 자원에 접근할 수 없습니다.");
        }
    }
}
