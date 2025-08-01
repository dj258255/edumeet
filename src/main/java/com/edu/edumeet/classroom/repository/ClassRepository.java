package com.edu.edumeet.classroom.repository;

import com.edu.edumeet.classroom.domain.ClassRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassRepository extends JpaRepository<ClassRoom, Long> {
    List<ClassRoom> findAllByMemberId(Long memberId);
}
