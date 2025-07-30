package com.edu.edumeet.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import net.coobird.thumbnailator.Thumbnailator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 업로드 과정
 * 1. MultipartFile 객체를 가지는 DTO 작성
 * 2. Controller는 MultipartFile을 이용 -> 서버의 특정한 폴더에 업로드 (이 과정에서 UUID를 이용해서 고유한 파일 이름 생성)
 * 3. 이미지 파일은 섬네일 파일도 같이 특정 폴더에 생성
 * 4. 특정 폴더에 생성된 파일의 이름을 이용해서 해당 파일들을 S3로 업로드하고 기존 파일들은 삭제.
 */

@Component
@Log4j2
public class LocalUploader {

    @Value("${edumeet.upload.path}")
    private String uploadPath;

    //디렉토리가 없으면 생성 
    @PostConstruct
    public void init(){
        log.info("=== 업로드 경로 설정 확인 ===");
        log.info("업로드 경로 : {}", uploadPath);

        //디렉토리가 없으면 생성
        File uploadDir = new File(uploadPath);
        if (!uploadDir.exists()){
            boolean created = uploadDir.mkdirs();
            if(created){
                log.info("업로드 디렉토리 생성 완료: {}" , uploadPath);
            } else{
                log.error("업로드 디렉토리 생성 실패 : {}", uploadPath);
            }
        } else {
            log.info("업로드 디렉토리 존재 확인 : {}", uploadPath);
        }
    }


    /**
     * MultipartFile 타입의 객체를 받아서 실제로 로컬폴더에 파일을 저장.
     * 이미지 파일일 경우엔 섬네일 생성.
     * uploadLocal() 의 리턴 값은 UUID 값이 붙은 실제 업로드된 파일의 절대 경로.
     * 만일 이미지 파일이 업로드되면 원본 파일의 경로와 섬네일 파일의 경로 2개가 List로 반환.
     */
    public List<String> uploadLocal(MultipartFile multipartFile){
        if(multipartFile == null || multipartFile.isEmpty()){
            return null;
        }

        String uuid = UUID.randomUUID().toString();
        String saveFileName = uuid+"_"+multipartFile.getOriginalFilename();

        Path savePath = Paths.get(uploadPath, saveFileName);

        List<String> savePathList = new ArrayList<>();

        try{
            multipartFile.transferTo(savePath);

            savePathList.add(savePath.toFile().getAbsolutePath());

            if(Files.probeContentType(savePath).startsWith("image")){
                File thumbFile = new File(uploadPath, "s_" + saveFileName);
                savePathList.add(thumbFile.getAbsolutePath());
                //이미지 리사이징
                Thumbnailator.createThumbnail(savePath.toFile(), thumbFile,200,200);
            }

        } catch (Exception e){
            log.error(e.getMessage());
            e.printStackTrace();
        }
        return savePathList;
    }

}
