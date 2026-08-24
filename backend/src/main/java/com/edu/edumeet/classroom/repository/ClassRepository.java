package com.edu.edumeet.classroom.repository;

import com.edu.edumeet.classroom.domain.ClassRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClassRepository extends JpaRepository<ClassRoom, Long> {
    List<ClassRoom> findAllByMemberIdAndIsDeletedFalse(Long memberId);

    Optional<ClassRoom> findByIdAndIsDeletedFalse(Long classId);
}
