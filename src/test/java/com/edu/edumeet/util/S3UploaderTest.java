package com.edu.edumeet.util;


import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@Log4j2
@ActiveProfiles("test") //H2 사용
public class S3UploaderTest {

    @Autowired
    private S3Uploader s3Uploader;


    //파일 올릴게 있고 테스트하고 싶으면 주석 빼기.
//    @Test
//    public void 테스트_원본파일제거_S3업로드(){
//        try{
//            String filePath = "/Users/beomsu/Pictures/test.png";
//            String uploadImageUrl = s3Uploader.upload(filePath);
//            log.info(uploadImageUrl);
//        } catch (Exception e){
//            log.error(e.getMessage());
//        }
//    }
}
