package com.edu.edumeet.classroom.domain;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "class_room_tags")
@Getter
public class Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private ClassRoom classRoom;

    private String name;
}
