package com.edu.edumeet.attachment.controller;

import com.edu.edumeet.attachment.service.AttachmentService;
import com.edu.edumeet.attachment.domain.Attachment;
import com.edu.edumeet.s3.util.S3Uploader;
import com.edu.edumeet.attachment.dto.AttachmentAdapter;
import com.edu.edumeet.attachment.dto.AttachmentUploadDTO;
import com.edu.edumeet.attachment.dto.AttachmentResultDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@Log4j2
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;
    private final AttachmentAdapter attachmentAdapter;
    private final S3Uploader s3Uploader;

    @Operation(summary = "파일 업로드", description = "POST 방식으로 파일 등록 (S3로)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "업로드 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<AttachmentResultDTO>> upload(AttachmentUploadDTO attachmentUploadDTO) {
        
        log.info("파일 업로드 요청 - 도메인: {}, 참조ID: {}, 파일 수: {}", 
                attachmentUploadDTO.getDomain(), attachmentUploadDTO.getReferenceId(),
                (attachmentUploadDTO.getFiles() != null) ? attachmentUploadDTO.getFiles().size() : 0);

        if (attachmentUploadDTO.getFiles() == null || attachmentUploadDTO.getFiles().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        List<Attachment> uploadedFiles = attachmentService.uploadFiles(
                attachmentUploadDTO.getFiles(),
                attachmentUploadDTO.getDomain(),
                attachmentUploadDTO.getReferenceId());
        
        if (uploadedFiles.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        // Attachment 객체를 UploadResultDTO로 변환
        List<AttachmentResultDTO> result = uploadedFiles.stream()
                .map(this::convertToUploadResultDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "파일 정보 조회", description = "파일 정보와 S3 URL을 JSON으로 반환합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "404", description = "파일을 찾을 수 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{fileName}")
    public ResponseEntity<Map<String, Object>> getFileInfo(
            @Parameter(description = "조회할 파일명", required = true)
            @PathVariable String fileName) {

        log.info("파일 정보 조회 요청: {}", fileName);

        try {
            Map<String, Object> fileInfo = attachmentService.getFileInfo(fileName);
            
            // 추가 정보 설정 (파일 확장자 등)
            if (!fileInfo.containsKey("fileExtension") && fileInfo.containsKey("fileName")) {
                String fname = (String) fileInfo.get("fileName");
                if (fname != null && fname.contains(".")) {
                    fileInfo.put("fileExtension", fname.substring(fname.lastIndexOf(".") + 1).toLowerCase());
                }
            }
            
            return ResponseEntity.ok(fileInfo);
        } catch (Exception e) {
            log.error("파일 정보 조회 실패: {} - {}", fileName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Operation(summary = "파일 삭제", description = "S3에서 파일을 삭제")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 성공"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @DeleteMapping("/{fileName}")
    public ResponseEntity<Map<String, Boolean>> removeFile(
            @Parameter(description = "삭제할 파일명", required = true)
            @PathVariable String fileName) {
        
        log.info("파일 삭제 요청: {}", fileName);

        boolean removed = attachmentService.removeFile(fileName);
        
        Map<String, Boolean> resultMap = new HashMap<>();
        resultMap.put("result", removed);
        
        return ResponseEntity.ok(resultMap);
    }

    @Operation(summary = "도메인별 파일 목록 조회", description = "특정 도메인과 참조 ID에 해당하는 파일 목록을 조회합니다")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/list/{domain}/{referenceId}")
    public ResponseEntity<List<AttachmentResultDTO>> getFilesByReference(
            @Parameter(description = "도메인", required = true) @PathVariable String domain,
            @Parameter(description = "참조 ID", required = true) @PathVariable Long referenceId) {
        
        log.info("도메인 {} 및 참조 ID {}에 대한 파일 목록 조회 요청", domain, referenceId);
        
        List<Attachment> files = attachmentService.getFilesByReference(domain, referenceId);
        
        List<AttachmentResultDTO> result = files.stream()
                .map(this::convertToUploadResultDTO)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Presigned URL 생성", description = "파일 업로드를 위한 presigned URL을 생성합니다. 도메인별로 구분하여 S3에 저장됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Presigned URL 생성 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/presigned-url")
    public ResponseEntity<Map<String, String>> generatePresignedUrl(
            @Parameter(description = "도메인 (예: assignments, boards, submissions)", required = true) 
            @RequestParam String domain,
            @Parameter(description = "파일명", required = true) 
            @RequestParam String fileName) {
        
        log.info("Presigned URL 생성 요청: domain={}, fileName={}", domain, fileName);
        
        try {
            // 입력값 검증
            if (domain == null || domain.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            if (fileName == null || fileName.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            String uuid = UUID.randomUUID().toString();
            String presignedUrl = s3Uploader.generatePresignedUrl(domain, uuid, fileName, Duration.ofMinutes(10));
            
            Map<String, String> result = new HashMap<>();
            result.put("presignedUrl", presignedUrl);
            result.put("uuid", uuid);
            result.put("fileName", fileName);
            result.put("domain", domain);
            
            log.info("Presigned URL 생성 성공: domain={}, uuid={}, fileName={}", domain, uuid, fileName);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Presigned URL 생성 실패: domain={}, fileName={} - {}", domain, fileName, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // Attachment 객체를 UploadResultDTO로 변환하는 헬퍼 메서드
    private AttachmentResultDTO convertToUploadResultDTO(Attachment attachment) {
        return attachmentAdapter.toDto(attachment);
    }
    
    // Attachment 객체를 응답용 Map으로 변환하는 헬퍼 메서드
    private Map<String, Object> convertToResponseMap(Attachment attachment) {
        AttachmentResultDTO dto = attachmentAdapter.toDto(attachment);
        
        Map<String, Object> map = new HashMap<>();
        map.put("uuid", dto.getUuid());
        map.put("fileName", dto.getFileName());
        map.put("isImage", dto.isImg());
        map.put("fileSize", dto.getFileSize());
        map.put("fileSizeInMB", dto.getFileSizeInMB());
        map.put("contentType", dto.getContentType());
        map.put("fileExtension", dto.getFileExtension());
        map.put("domain", dto.getDomain());
        map.put("referenceId", dto.getReferenceId());
        map.put("uploadedAt", dto.getUploadedAt());
        map.put("uploadedBy", dto.getUploadedBy());
        map.put("originalUrl", dto.getOriginalUrl());
        
        if (dto.isImg()) {
            map.put("thumbnailUrl", dto.getThumbnailUrl());
        }
        
        return map;
    }
    
}