package com.edu.edumeet.board.presentation;

import com.edu.edumeet.board.presentation.dto.upload.SampleDTO;
import com.edu.edumeet.util.LocalUploader;
import com.edu.edumeet.util.S3Uploader;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 업로드된 파일들은 로컬에 우선 저장
 * -> 이미지일 경우엔 섬네일 처리 끝나고
 * -> 해당 파일들 모두 S3로 업로드 -> 원본파일 삭제
 * upload()리턴값은 S3에 업로드된 파일들의 경로로 브라우저에서 확인이 가능함.
 */
@RestController
@Log4j2
@RequiredArgsConstructor
@RequestMapping("/api/v1/sample")
public class SampleController {

    private final LocalUploader localUploader;

    private final S3Uploader s3Uploader;

    @PostMapping("/upload")
    public List<String> upload(SampleDTO sampleDTO){
        MultipartFile[] files = sampleDTO.getFiles();

        if(files == null || files.length <= 0){
            return null;
        }

        List<String> uploadedFilePaths = new ArrayList<>();

        for(MultipartFile file : files){
            uploadedFilePaths.addAll(localUploader.uploadLocal(file));
        }
        log.info("-------------------------");
        log.info(uploadedFilePaths);

        //s3Path
        return uploadedFilePaths.stream()
                .map(s3Uploader::upload)
                .collect(Collectors.toList());
    }
}
