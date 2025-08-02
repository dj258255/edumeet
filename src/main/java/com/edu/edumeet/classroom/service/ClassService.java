package com.edu.edumeet.classroom.service;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.classroom.domain.Tag;
import com.edu.edumeet.classroom.domain.Thumbnail;
import com.edu.edumeet.classroom.dto.request.ClassCreateRequestDto;
import com.edu.edumeet.classroom.dto.response.ClassInfoResponseDto;
import com.edu.edumeet.classroom.repository.ClassRepository;
import com.edu.edumeet.classroom.repository.TagRepository;
import com.edu.edumeet.classroom.repository.ThumbnailRepository;
import com.edu.edumeet.member.infrastructure.MemberJpaEntity;
import com.edu.edumeet.member.infrastructure.MemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassService {
    private final ClassRepository classRepository;
    private final ThumbnailRepository thumbnailRepository;
    private final TagRepository tagRepository;
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
        List<ClassRoom> classRooms = classRepository.findAllByMemberId(memberId);

        return classRooms.stream()
            .map(classRoom -> ClassInfoResponseDto.builder()
                .title(classRoom.getTitle())
                .description(classRoom.getDescription())
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
}
