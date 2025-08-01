package com.edu.edumeet.classroom.repository;

import com.edu.edumeet.classroom.domain.ClassRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassRepository extends JpaRepository<ClassRoom, Long> {
}
