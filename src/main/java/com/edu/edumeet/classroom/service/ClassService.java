package com.edu.edumeet.classroom.service;

import com.edu.edumeet.classroom.domain.ClassRoom;
import com.edu.edumeet.classroom.dto.request.ClassCreateRequestDto;
import com.edu.edumeet.classroom.dto.response.ClassInfoResponseDto;
import com.edu.edumeet.classroom.repository.ClassRepository;
import com.edu.edumeet.member.infrastructure.MemberJpaEntity;
import com.edu.edumeet.member.infrastructure.MemberJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassService {
    private final ClassRepository classRepository;
    private final MemberJpaRepository memberJpaRepository;

    public void create(Long memberId, ClassCreateRequestDto classCreateRequestDto) {
        String title = classCreateRequestDto.getTitle();
        String description = classCreateRequestDto.getDescription();
        int limit = classCreateRequestDto.getLimit();

        MemberJpaEntity member = memberJpaRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("해당 회원이 존재하지 않습니다."));

        classRepository.save(ClassRoom.builder()
                        .member(member)
                        .title(title)
                        .description(description)
                        .participantLimit(limit)
                        .build());
    }

    public List<ClassInfoResponseDto> getMyClasses(Long memberId) {
        List<ClassRoom> classRooms = classRepository.findAllByMemberId(memberId);
        return classRooms.stream()
                .map(classRoom -> ClassInfoResponseDto.builder()
                        .title(classRoom.getTitle())
                        .description(classRoom.getDescription())
                        .build())
                .toList();
    }
}
