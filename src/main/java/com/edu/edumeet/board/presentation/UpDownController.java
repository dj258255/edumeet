package com.edu.edumeet.board.presentation;

import com.edu.edumeet.board.presentation.dto.upload.UploadFileDTO;
import com.edu.edumeet.board.presentation.dto.upload.UploadResultDTO;
import com.edu.edumeet.util.LocalUploader;
import com.edu.edumeet.util.S3Uploader;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.coobird.thumbnailator.Thumbnailator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@Log4j2
@RequiredArgsConstructor
public class UpDownController {

    @Value("${edumeet.upload.path}")
    private String uploadPath;

    private final LocalUploader localUploader;
    private final S3Uploader s3Uploader;

    //첨부파일 등록

    @Operation(summary = "업로드 Post",description = "Post 방식으로 파일 등록 (S3로)")
    public List<UploadResultDTO> upload(UploadFileDTO uploadFileDTO){
        log.info(uploadFileDTO);

        if(uploadFileDTO.getFiles() != null){
            final List<UploadResultDTO> list = new ArrayList<>();

            uploadFileDTO.getFiles().forEach(multipartFile -> {
                    //1. 로컬에 먼저 업로드(섬네일 까지)
                    List<String> localPaths = localUploader.uploadLocal(multipartFile);

                if (localPaths != null && !localPaths.isEmpty()


                ) {

                    String originalName = multipartFile.getOriginalFilename();
                    String uuid = extraUuidFromPath(localPaths.get(0));

                    //2. S3로 업로드하고 로컬 파일 삭제
                    List<String> s3Urls = localPaths.stream()
                            .map(s3Uploader::upload)
                            .collect(Collectors.toList());


                    boolean isImage = s3Urls.size() > 1; //섬네일 없으면 이미

                    list.add(UploadResultDTO.builder()
                            .uuid(uuid)
                            .fileName(originalName)
                            .img(isImage)
                            .build());
                }
            });

            return list;
        }
        return null;
    }

    //파일 경로에서 UUID 추출하는 헬퍼 메서드
    private String extraUuidFromPath(String filePath){
        String fileName = new File(filePath).getName();
        int underscoreIndex = fileName.indexOf('_');
        return underscoreIndex > 0 ? fileName.substring(0, underscoreIndex) : UUID.randomUUID().toString();
    }



//    @Operation(summary = "업로드 Post", description = "POST방식으로 파일 등록")
//    @PostMapping(value = "/api/v1/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public List<UploadResultDTO> upload(UploadFileDTO uploadFileDTO){
//        log.info(uploadFileDTO);
//
//        if(uploadFileDTO.getFiles() != null){
//
//            final List<UploadResultDTO> list = new ArrayList<>();
//
//            uploadFileDTO.getFiles().forEach(multipartFile -> {
//                //1. 로컬에 먼저 업로드 (섬네일 생성 포함)
//                String originalName = multipartFile.getOriginalFilename();
//                log.info(multipartFile.getOriginalFilename());
//
//                String uuid = UUID.randomUUID().toString();
//
//                Path savePath = Paths.get(uploadPath, uuid+"_"+ originalName);
//
//                boolean image = false;
//
//                try {
//                    multipartFile.transferTo(savePath);
//
//                    //이미지 파일의 종류라면
//                    if(Files.probeContentType(savePath).startsWith("image")){
//                        image = true;
//
//                        File thumbFile = new File(uploadPath, "s_"+uuid+"_"+originalName);
//
//                        Thumbnailator.createThumbnail(savePath.toFile(), thumbFile, 200, 200);
//                    }
//                } catch (IOException e){
//                    e.printStackTrace();
//                }
//
//                list.add(UploadResultDTO.builder()
//                        .uuid(uuid)
//                        .fileName(originalName)
//                        .img(image).build()
//                );
//            });
//
//            return list;
//        }
//        return null;
//    }


    //첨부파일 조회
    public ResponseEntity<Void> viewFileGET(
            @PathVariable String fileName){
        //S3 url로 리다이렉트
        String s3Url = getS3Url(fileName);
        return ResponseEntity.status(302)
                .header("Location", s3Url)
                .build();
    }

    // S3 URL 생성 헬퍼 메서드
    private String getS3Url(String fileName) {
        // application.properties에서 bucket name을 가져오거나 하드코딩
        // 여기서는 S3Uploader의 형식을 따라 생성
        return String.format("https://your-bucket-name.s3.amazonaws.com/%s", fileName);
    }


//    @Operation(summary = "view 파일" , description = "GET방식으로 첨부파일 조회")
//    @GetMapping("/api/v1/view/{fileName}")
//    public ResponseEntity<Resource> viewFileGET(@PathVariable String fileName){
//
//        Resource resource = new FileSystemResource(uploadPath+File.separator + fileName);
//
//        String resourceName = resource.getFilename();
//        HttpHeaders headers = new HttpHeaders();
//
//        try{
//            headers.add("Content-Type", Files.probeContentType(resource.getFile().toPath()));
//        } catch ( Exception e){
//            return ResponseEntity.internalServerError().build();
//        }
//        return ResponseEntity.ok().headers(headers).body(resource);
//    }


    //첨부파일 삭제

    public Map<String,Boolean> removeFile(
            @PathVariable String fileName
    ){
        Map<String, Boolean> resultMap = new HashMap<>();
        boolean removed = false;

        try{
            //S3에서 파일 삭제
            s3Uploader.removeS3File(fileName);

            //이미지인 경우 섬네일 삭제
            if(isImageFile(fileName)){
                String thumbnailFileName = "s_" + fileName;
                s3Uploader.removeS3File(thumbnailFileName);
            }

            removed = true;
        } catch (Exception e){
            log.error("S3 파일 삭제 실패 : {}", e.getMessage());
        }

        resultMap.put("result", removed);
        return resultMap;
    }

    //파일 이미지인지 확인
    public boolean isImageFile(String fileName){
        String extension = fileName.substring(fileName.lastIndexOf(".")+1).toLowerCase();
        return Arrays.asList("jpg", "jpeg", "png", "gif", "bmp", "webp").contains(extension);
    }

//
//    @Operation(summary = "remove 파일", description = "DELETE 방식으로 파일 삭제")
//    public Map<String,Boolean> removeFile(@PathVariable String fileName){
//        Resource resource = new FileSystemResource(uploadPath+File.separator + fileName);
//
//        String resourceName = resource.getFilename();
//
//        Map<String, Boolean> resultMap = new HashMap<>();
//        boolean removed = false;
//
//        try{
//            String contentType = Files.probeContentType(resource.getFile().toPath());
//            removed = resource.getFile().delete();
//
//            //섬네일이 있으면
//            if(contentType.startsWith("image")){
//                File thumbnailFile = new File(uploadPath+File.separator + "s_" + fileName);
//
//                thumbnailFile.delete();
//            }
//
//
//        } catch (Exception e){
//            log.error(e.getMessage());
//        }
//
//        resultMap.put("result", removed);
//
//        return resultMap;
//    }

}
