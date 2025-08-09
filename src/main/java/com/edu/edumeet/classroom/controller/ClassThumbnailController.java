package com.edu.edumeet.classroom.controller;

import com.edu.edumeet.classroom.service.ClassThumbnailService;
import com.edu.edumeet.upload.presentation.dto.UploadFileDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Log4j2
@RequestMapping("/api/v1/classroom/thumbnail")
@RequiredArgsConstructor
public class ClassThumbnailController {

    private final ClassThumbnailService classThumbnailService;

    @PostMapping(consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<com.edu.edumeet.classroom.dto.response.ThumbnailUploadResultDto> uploadThumbnail(
            UploadFileDTO uploadFileDTO) {


        log.info("클래스 썸네일 업로드 요청: {}", uploadFileDTO);

        if (uploadFileDTO.getFiles() == null || uploadFileDTO.getFiles().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        com.edu.edumeet.classroom.dto.response.ThumbnailUploadResultDto result = 
            classThumbnailService.uploadThumbnail(uploadFileDTO.getFiles().get(0));

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{uuid}")
    public ResponseEntity<String> deleteThumbnail(@PathVariable String uuid) {
        log.info("썸네일 삭제 요청: {}", uuid);
        
        boolean deleted = classThumbnailService.deleteThumbnail(uuid);
        
        if (deleted) {
            return ResponseEntity.ok("썸네일이 성공적으로 삭제되었습니다.");
        } else {
            return ResponseEntity.badRequest().body("썸네일 삭제에 실패했습니다.");
        }
    }
    
    // 디버깅용: 임시 저장소 상태 확인
    @GetMapping("/debug/storage")
    public ResponseEntity<Map<String, Object>> getStorageStatus() {
        Map<String, com.edu.edumeet.classroom.dto.response.ThumbnailUploadResultDto> storage = 
            classThumbnailService.getTemporaryStorage();
        
        return ResponseEntity.ok(Map.of(
            "count", storage.size(),
            "thumbnails", storage
        ));
    }
    
    // 디버깅용: 특정 UUID 확인
    @GetMapping("/debug/{uuid}")
    public ResponseEntity<Object> getThumbnailInfo(@PathVariable String uuid) {
        return classThumbnailService.getThumbnailInfo(uuid)
            .map(info -> ResponseEntity.ok((Object) info))
            .orElse(ResponseEntity.ok(Map.of("error", "썸네일을 찾을 수 없습니다", "uuid", uuid)));
    }
}
