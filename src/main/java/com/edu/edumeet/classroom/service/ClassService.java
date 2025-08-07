package com.edu.edumeet.classroom.service;

import com.edu.edumeet.classroom.domain.*;
import com.edu.edumeet.classroom.dto.request.ClassCreateRequestDto;
import com.edu.edumeet.classroom.dto.request.ClassStatusChangeRequestDto;
import com.edu.edumeet.classroom.dto.request.EvictionRequestDto;
import com.edu.edumeet.classroom.dto.response.ClassInfoResponseDto;
import com.edu.edumeet.classroom.dto.response.ThumbnailUploadResultDto;
import com.edu.edumeet.classroom.repository.*;
import com.edu.edumeet.member.domain.Member;
import com.edu.edumeet.member.dto.response.SignupResponseDto;
import com.edu.edumeet.member.repository.MemberRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Log4j2
public class ClassService {
    private final ClassRepository classRepository;
    private final TagRepository tagRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ClassInviteRepository classInviteRepository;
    private final MemberRepository memberRepository;
    private final ClassThumbnailService classThumbnailService;

    /**
     * 클래스 생성 (썸네일 정보 포함)
     */
    @Transactional
    public void create(Long memberId, ClassCreateRequestDto classCreateRequestDto) {
        Member member = getMemberOrThrow(memberId);

        // 1. 썸네일 정보 조회 (UUID가 있는 경우)
        String thumbnailUuid = classCreateRequestDto.getThumbnailUuid();
        String thumbnailUrl = null;
        String originalUrl = null;
        
        if (thumbnailUuid != null && !thumbnailUuid.trim().isEmpty()) {
            Optional<ThumbnailUploadResultDto> thumbnailInfo = 
                    classThumbnailService.getThumbnailInfo(thumbnailUuid);
            
            if (thumbnailInfo.isPresent()) {
                ThumbnailUploadResultDto thumbnail = thumbnailInfo.get();
                thumbnailUrl = thumbnail.getThumbnailUrl();
                originalUrl = thumbnail.getOriginalUrl();
                
                // 임시 저장소에서 제거 (사용 완료)
                classThumbnailService.markThumbnailAsUsed(thumbnailUuid);
                
                log.info("썸네일 정보 연결 - UUID: {}, 썸네일 URL: {}", thumbnailUuid, thumbnailUrl);
            } else {
                log.warn("썸네일을 찾을 수 없습니다 - UUID: {} (만료되었거나 존재하지 않음)", thumbnailUuid);
                // 썸네일이 없어도 클래스 생성은 계속 진행
                thumbnailUuid = null;
            }
        }

        // 2. 클래스룸 생성 (썸네일 정보 포함)
        ClassRoom classRoom = ClassRoom.builder()
                .member(member)
                .title(classCreateRequestDto.getTitle())
                .description(classCreateRequestDto.getDescription())
                .participantLimit(classCreateRequestDto.getLimit())
                .thumbnailUuid(thumbnailUuid)
                .thumbnailUrl(thumbnailUrl)
                .originalUrl(originalUrl)
                .build();
        
        ClassRoom savedClassRoom = classRepository.save(classRoom);

        // 3. 태그 저장
        if (classCreateRequestDto.getTags() != null && !classCreateRequestDto.getTags().isEmpty()) {
            saveTags(classCreateRequestDto.getTags(), savedClassRoom);
        }

        log.info("클래스 생성 완료 - ID: {}, 제목: {}, 썸네일 포함: {}", 
                savedClassRoom.getId(), savedClassRoom.getTitle(), savedClassRoom.hasThumbnail());
    }

    private ClassRoom saveClassRoom(ClassCreateRequestDto classCreateRequestDto, Member member) {
        return ClassRoom.builder()
                .member(member)
                .title(classCreateRequestDto.getTitle())
                .description(classCreateRequestDto.getDescription())
                .participantLimit(classCreateRequestDto.getLimit())
                .build();
    }

    private Member getMemberOrThrow(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원이 존재하지 않습니다."));
    }

    private void saveTags(List<String> tags, ClassRoom classRoom) {
        List<Tag> tagEntities = tags.stream()
                .map(tagName -> Tag.builder()
                        .classRoom(classRoom)
                        .name(tagName)
                        .build())
                .toList();
        tagRepository.saveAll(tagEntities);
    }

    /**
     * 내가 생성한 클래스 목록 조회 (간소화됨!)
     */
    public List<ClassInfoResponseDto> getMyClasses(Long memberId) {
        List<ClassRoom> classRooms = classRepository.findAllByMemberIdAndIsDeletedFalse(memberId);

        return classRooms.stream()
            .map(classRoom -> ClassInfoResponseDto.builder()
                .classId(classRoom.getId())
                .title(classRoom.getTitle())
                .description(classRoom.getDescription())
                .participantLimit(classRoom.getParticipantLimit())
                .thumbnailUrl(classRoom.getThumbnailUrl())  // 직접 접근! JOIN 불필요
                .tags(classRoom.getTags() != null 
                      ? classRoom.getTags().stream().map(Tag::getName).toList() 
                      : List.of())
                .build())
            .toList();
    }

    /**
     * 참여한 클래스 목록 조회
     */
    public List<ClassInfoResponseDto> getJoinedClasses(Long memberId) {
        List<ClassMember> classMembers = classMemberRepository.findAllByMemberId(memberId);

        return classMembers.stream()
                .map(ClassMember::getClassRoom)
                .map(classRoom -> ClassInfoResponseDto.builder()
                        .classId(classRoom.getId())
                        .title(classRoom.getTitle())
                        .description(classRoom.getDescription())
                        .participantLimit(classRoom.getParticipantLimit())
                        .thumbnailUrl(classRoom.getThumbnailUrl())  // 직접 접근!
                        .tags(Optional.ofNullable(classRoom.getTags())
                                .orElse(List.of())
                                .stream()
                                .map(Tag::getName)
                                .toList())
                        .build())
                .toList();
    }

    /**
     * 클래스 상세 조회
     */
    public ClassInfoResponseDto getClassDetail(Long classRoomId) {
        ClassRoom classRoom = classRepository.findById(classRoomId)
                .filter(c -> Boolean.FALSE.equals(c.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("클래스를 찾을 수 없습니다."));

        return ClassInfoResponseDto.builder()
                .classId(classRoom.getId())
                .title(classRoom.getTitle())
                .description(classRoom.getDescription())
                .participantLimit(classRoom.getParticipantLimit())
                .thumbnailUrl(classRoom.getThumbnailUrl())  // 직접 접근!
                .tags(Optional.ofNullable(classRoom.getTags())
                        .orElse(List.of())
                        .stream()
                        .map(Tag::getName)
                        .toList())
                .build();
    }

    @Transactional
    public void delete(Long memberId, Long classRoomId) {
        ClassRoom classRoom = classRepository.findById(classRoomId)
                .orElseThrow(() -> new IllegalArgumentException("해당 클래스를 찾을 수 없습니다."));

        if (!classRoom.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("클래스를 삭제할 권한이 없습니다.");
        }

        classRoom.markAsDeleted();
        
        // 썸네일이 있다면 S3에서도 삭제 (선택사항)
        if (classRoom.hasThumbnail()) {
            classThumbnailService.deleteThumbnail(classRoom.getThumbnailUuid());
        }
    }

    // 나머지 메서드들은 기존과 동일...
    public void inviteStudents(Long classId, Long memberId, List<String> emails) {
        ClassRoom classRoom = classRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("해당 클래스는 존재하지 않습니다."));

        if (!classRoom.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        for (String email : emails) {
            Member invitee = memberRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalArgumentException("해당 회원이 존재하지 않습니다."));

            boolean alreadyInvited = classInviteRepository.existsByClassRoomAndInvitee(classRoom, invitee);
            if (!alreadyInvited) {
                ClassInvite invite = ClassInvite.builder()
                        .classRoom(classRoom)
                        .invitee(invitee)
                        .status(InviteStatus.APPLIED)
                        .build();
                classInviteRepository.save(invite);
            }
        }
    }

    public List<ClassInfoResponseDto> getInvitedClass(Long memberId) {
        List<ClassInvite> invites = classInviteRepository.findByInviteeId(memberId);

        return invites.stream()
                .filter(invite -> invite.getStatus() == InviteStatus.APPLIED)
                .map(ClassInvite::getClassRoom)
                .filter(classRoom -> !classRoom.getIsDeleted())
                .map(classRoom -> ClassInfoResponseDto.builder()
                        .classId(classRoom.getId())
                        .title(classRoom.getTitle())
                        .description(classRoom.getDescription())
                        .thumbnailUrl(classRoom.getThumbnailUrl())  // 직접 접근!
                        .tags(classRoom.getTags().stream()
                                .map(Tag::getName)
                                .toList())
                        .participantLimit(classRoom.getParticipantLimit())
                        .build())
                .toList();
    }

    @Transactional
    public void changeStatus(Long memberId, ClassStatusChangeRequestDto classStatusChangeRequestDto) {
        InviteStatus newStatus = classStatusChangeRequestDto.getStatus();

        if (newStatus != InviteStatus.ACCEPTED && newStatus != InviteStatus.DENIED) {
            throw new IllegalArgumentException("유효하지 않은 초대 상태입니다.");
        }

        ClassInvite invite = classInviteRepository.findByClassRoomIdAndInviteeId(classStatusChangeRequestDto.getClassId(), memberId)
                .orElseThrow(() -> new IllegalArgumentException("초대 정보를 찾을 수 없습니다."));

        if (invite.getStatus() != InviteStatus.APPLIED) {
            throw new IllegalArgumentException("이미 응답한 초대는 상태를 변경할 수 없습니다.");
        }

        if (newStatus == InviteStatus.ACCEPTED) {
            ClassMember classMember = ClassMember.builder()
                    .classRoom(invite.getClassRoom())
                    .member(invite.getInvitee())
                    .build();
            classMemberRepository.save(classMember);

            classInviteRepository.delete(invite);
        } else {
            invite.changeStatus(InviteStatus.DENIED);
        }
    }

    public List<SignupResponseDto> getClassMembers(Long memberId, Long classId) {
        ClassRoom classRoom = classRepository.findByIdAndIsDeletedFalse(classId)
                .orElseThrow(() -> new IllegalArgumentException("해당 클래스를 찾을 수 없습니다."));

        boolean isMember = classMemberRepository.existsByClassRoomIdAndMemberId(classId, memberId)
                || classRoom.getMember().getId().equals(memberId);
        if (!isMember) {
            throw new IllegalArgumentException("해당 클래스에 접근할 수 없습니다.");
        }

        List<ClassMember> classMembers = classMemberRepository.findAllByClassRoomId(classId);
        Member owner = classRoom.getMember();
        List<SignupResponseDto> result = new ArrayList<>();

        result.add(SignupResponseDto.builder()
                .email(owner.getEmail())
                .nickname(owner.getNickname())
                .build());

        result.addAll(classMembers.stream()
                .map(ClassMember::getMember)
                .filter(member -> !member.getId().equals(owner.getId()))
                .map(member -> SignupResponseDto.builder()
                        .email(member.getEmail())
                        .nickname(member.getNickname())
                        .build())
                .toList());

        return result;
    }

    @Transactional
    public void evictStudent(Long requesterId, EvictionRequestDto evictionRequestDto) {
        Long classId = evictionRequestDto.getClassId();
        String email = evictionRequestDto.getEmail();

        Member memberEntity = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("해당 이메일의 회원이 존재하지 않습니다."));

        Long studentId = memberEntity.getId();

        ClassRoom classRoom = classRepository.findByIdAndIsDeletedFalse(classId)
                .orElseThrow(() -> new IllegalArgumentException("해당 클래스가 존재하지 않습니다."));

        if (!classRoom.getMember().getId().equals(requesterId)) {
            throw new IllegalArgumentException("해당 작업에 대한 권한이 없습니다.");
        }

        if (requesterId.equals(studentId)) {
            throw new IllegalArgumentException("방장은 스스로를 강제 퇴장시킬 수 없습니다.");
        }

        ClassMember classMember = classMemberRepository.findByClassRoomIdAndMemberId(classId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("클래스에 해당 학생이 존재하지 않습니다."));

        classMemberRepository.delete(classMember);
    }
}
