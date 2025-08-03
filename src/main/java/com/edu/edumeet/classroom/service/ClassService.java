package com.edu.edumeet.classroom.service;

import com.edu.edumeet.classroom.domain.ClassMember;
import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.classroom.domain.Tag;
import com.edu.edumeet.classroom.domain.Thumbnail;
import com.edu.edumeet.classroom.dto.request.ClassCreateRequestDto;
import com.edu.edumeet.classroom.dto.response.ClassInfoResponseDto;
import com.edu.edumeet.classroom.repository.ClassMemberRepository;
import com.edu.edumeet.classroom.repository.ClassRepository;
import com.edu.edumeet.classroom.repository.TagRepository;
import com.edu.edumeet.classroom.repository.ThumbnailRepository;
import com.edu.edumeet.member.infrastructure.MemberJpaEntity;
import com.edu.edumeet.member.infrastructure.MemberJpaRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClassService {
    private final ClassRepository classRepository;
    private final ThumbnailRepository thumbnailRepository;
    private final TagRepository tagRepository;
    private final ClassMemberRepository classMemberRepository;
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
}
