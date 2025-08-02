package com.edu.edumeet.classroom.domain;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "class_room_thumbnail")
@Getter
public class Thumbnail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "class_room_id")
    private ClassRoom classRoom;

    @Column(nullable = false)
    private String imageUrl;
}
