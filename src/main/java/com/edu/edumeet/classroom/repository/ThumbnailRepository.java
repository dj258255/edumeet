package com.edu.edumeet.classroom.repository;

import com.edu.edumeet.classroom.domain.Thumbnail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ThumbnailRepository extends JpaRepository<Thumbnail, Long> {
}
