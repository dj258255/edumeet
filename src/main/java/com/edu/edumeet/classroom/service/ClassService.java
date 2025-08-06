package com.edu.edumeet.classroom.service;

import com.edu.edumeet.classroom.domain.*;
import com.edu.edumeet.classroom.dto.request.ClassCreateRequestDto;
import com.edu.edumeet.classroom.dto.request.ClassStatusChangeRequestDto;
import com.edu.edumeet.classroom.dto.request.EvictionRequestDto;
import com.edu.edumeet.classroom.dto.response.ClassInfoResponseDto;
import com.edu.edumeet.classroom.repository.*;
import com.edu.edumeet.member.infrastructure.MemberJpaEntity;
import com.edu.edumeet.member.infrastructure.MemberJpaRepository;
import com.edu.edumeet.member.presentation.dto.response.SignupResponseDto;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClassService {
    private final ClassRepository classRepository;
    private final ThumbnailRepository thumbnailRepository;
    private final TagRepository tagRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ClassInviteRepository classInviteRepository;
    private final MemberJpaRepository memberJpaRepository;

    public void create(Long memberId, ClassCreateRequestDto classCreateRequestDto) {
        MemberJpaEntity member = getMemberOrThrow(memberId);

        ClassRoom classRoom = saveClassRoom(classCreateRequestDto, member);
        saveThumbnail(classCreateRequestDto.getThumbnailUrl(), classRoom);
        saveTags(classCreateRequestDto.getTags(), classRoom);
    }

    private ClassRoom saveClassRoom(ClassCreateRequestDto classCreateRequestDto, MemberJpaEntity member) {
        ClassRoom classRoom = ClassRoom.builder()
                .member(member)
                .title(classCreateRequestDto.getTitle())
                .description(classCreateRequestDto.getDescription())
                .participantLimit(classCreateRequestDto.getLimit())
                .build();
        return classRepository.save(classRoom);
    }

    private MemberJpaEntity getMemberOrThrow(Long memberId) {
        return memberJpaRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원이 존재하지 않습니다."));
    }

    private void saveThumbnail(String imageUrl, ClassRoom classRoom) {
        thumbnailRepository.save(Thumbnail.builder()
                        .classRoom(classRoom)
                        .imageUrl(imageUrl)
                        .build());
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

    public List<ClassInfoResponseDto> getMyClasses(Long memberId) {
        List<ClassRoom> classRooms = classRepository.findAllByMemberIdAndIsDeletedFalse(memberId);

        return classRooms.stream()
            .map(classRoom -> ClassInfoResponseDto.builder()
                .classId(classRoom.getId())
                .title(classRoom.getTitle())
                .description(classRoom.getDescription())
                .participantLimit(classRoom.getParticipantLimit())
                .thumbnailUrl(
                    classRoom.getThumbnail() != null ? classRoom.getThumbnail().getImageUrl() : null
                )
                .tags(
                    classRoom.getTags() != null
                        ? classRoom.getTags().stream()
                        .map(Tag::getName)
                        .toList()
                        : List.of()
                )
                .build())
            .toList();
    }

    public List<ClassInfoResponseDto> getJoinedClasses(Long memberId) {
        List<ClassMember> classMembers = classMemberRepository.findAllByMemberId(memberId);

        return classMembers.stream()
                .map(ClassMember::getClassRoom)
                .map(classRoom -> ClassInfoResponseDto.builder()
                        .classId(classRoom.getId())
                        .title(classRoom.getTitle())
                        .description(classRoom.getDescription())
                        .participantLimit(classRoom.getParticipantLimit())
                        .thumbnailUrl(
                                Optional.ofNullable(classRoom.getThumbnail())
                                        .map(Thumbnail::getImageUrl)
                                        .orElse(null)
                        )
                        .tags(
                                Optional.ofNullable(classRoom.getTags())
                                        .orElse(List.of())
                                        .stream()
                                        .map(Tag::getName)
                                        .toList()
                        )
                        .build())
                .toList();
    }

    public ClassInfoResponseDto getClassDetail(Long classRoomId) {
        ClassRoom classRoom = classRepository.findById(classRoomId)
                .filter(c -> Boolean.FALSE.equals(c.getIsDeleted()))
                .orElseThrow(() -> new IllegalArgumentException("클래스를 찾을 수 없습니다."));

        return ClassInfoResponseDto.builder()
                .classId(classRoom.getId())
                .title(classRoom.getTitle())
                .description(classRoom.getDescription())
                .participantLimit(classRoom.getParticipantLimit())
                .thumbnailUrl(
                        Optional.ofNullable(classRoom.getThumbnail())
                                .map(Thumbnail::getImageUrl)
                                .orElse(null)
                )
                .tags(
                        Optional.ofNullable(classRoom.getTags())
                                .orElse(List.of())
                                .stream()
                                .map(Tag::getName)
                                .toList()
                )
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
    }

    public void inviteStudents(Long classId, Long memberId, List<String> emails) {
        ClassRoom classRoom = classRepository.findById(classId)
                .orElseThrow(() -> new IllegalArgumentException("해당 클래스는 존재하지 않습니다."));

        if (!classRoom.getMember().getId().equals(memberId)) {
            throw new IllegalArgumentException("권한이 없습니다.");
        }

        for (String email : emails) {
            MemberJpaEntity invitee = memberJpaRepository.findByEmail(email)
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
                .map(invite -> {
                    ClassRoom classRoom = invite.getClassRoom();
                    return ClassInfoResponseDto.builder()
                            .classId(classRoom.getId())
                            .title(classRoom.getTitle())
                            .description(classRoom.getDescription())
                            .thumbnailUrl(
                                    classRoom.getThumbnail() != null
                                            ? classRoom.getThumbnail().getImageUrl()
                                            : null
                            )
                            .tags(classRoom.getTags().stream()
                                    .map(Tag::getName)
                                    .toList())
                            .participantLimit(classRoom.getParticipantLimit())
                            .build();
                })
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
        }
        else {
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

        MemberJpaEntity owner = classRoom.getMember();

        List<SignupResponseDto> result = new ArrayList<>();

        result.add(SignupResponseDto.builder()
                .email(owner.getEmail())
                .nickname(owner.getNickname())
                .build());

        result.addAll(classMembers.stream()
                .map(ClassMember::getMember)
                .filter(member -> !member.getId().equals(owner.getId())) // 방장 중복 방지
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
        Long studentId = evictionRequestDto.getStudentId();

        ClassRoom classRoom = classRepository.findByIdAndIsDeletedFalse(classId)
                .orElseThrow(() -> new IllegalArgumentException("해당 클래스가 존재하지 않습니다."));

        if (!classRoom.getMember().getId().equals(requesterId)) {
            throw new IllegalArgumentException("해당 작업에 대한 권한이 없습니다."); // 또는 CLASS_OWNER_ONLY
        }

        if (requesterId.equals(studentId)) {
            throw new IllegalArgumentException("방장은 스스로를 강제 퇴장시킬 수 없습니다.");
        }

        ClassMember classMember = classMemberRepository.findByClassRoomIdAndMemberId(classId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("클래스에 해당 학생이 존재하지 않습니다."));

        classMemberRepository.delete(classMember);
    }
}
