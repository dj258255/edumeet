-- 데이터베이스 스키마 수정 스크립트
-- 1. email 기반 시스템으로 변경
-- 2. SubmissionFileUploadJpaEntity와 StudentSubmissionFileJpaEntity 통합

-- === Phase 1: Email 기반 시스템 변경 ===

-- 1-1. submission 테이블 수정: class_member_id -> class_member_email
ALTER TABLE submission 
ADD COLUMN class_member_email VARCHAR(100);

-- 기존 데이터가 있다면 Member 테이블과 조인하여 email 정보 설정
-- UPDATE submission s 
-- SET class_member_email = (SELECT m.email FROM member m WHERE m.id = s.class_member_id);

-- 1-2. student_submission_status 테이블 수정: student_id -> student_email  
ALTER TABLE student_submission_status 
ADD COLUMN student_email VARCHAR(100);

-- 기존 데이터가 있다면 Member 테이블과 조인하여 email 정보 설정
-- UPDATE student_submission_status sss 
-- SET student_email = (SELECT m.email FROM member m WHERE m.id = sss.student_id);

-- === Phase 2: 파일 엔티티 통합 ===

-- 2-1. submission_file_upload 테이블에 student_submission_status_id 컬럼 추가
ALTER TABLE submission_file_upload 
ADD COLUMN student_submission_status_id BIGINT NULL;

-- 2-2. submission_file_upload 테이블에 외래키 제약조건 추가
ALTER TABLE submission_file_upload 
ADD CONSTRAINT FK_submission_file_upload_student_submission_status 
FOREIGN KEY (student_submission_status_id) REFERENCES student_submission_status(id) ON DELETE CASCADE;

-- 2-3. student_submission_file의 데이터를 submission_file_upload로 이관 (데이터가 있는 경우)
-- INSERT INTO submission_file_upload (
--     student_submission_status_id, uuid, file_name, ord, img, file_size, 
--     content_type, uploaded_by, reference_id, domain, reg_date, mod_date
-- )
-- SELECT 
--     student_submission_status_id, uuid, file_name, ord, img, file_size,
--     content_type, uploaded_by, reference_id, domain, reg_date, mod_date
-- FROM student_submission_file;

-- 2-4. student_submission_file 테이블 삭제 (데이터 이관 후)
-- DROP TABLE IF EXISTS student_submission_file;

-- === Phase 3: 인덱스 및 성능 최적화 ===

-- 3-1. 인덱스 추가
CREATE INDEX idx_submission_file_upload_student_status 
ON submission_file_upload(student_submission_status_id);

CREATE INDEX idx_submission_file_upload_submission 
ON submission_file_upload(submission_id);

CREATE INDEX idx_submission_class_member_email 
ON submission(class_member_email);

CREATE INDEX idx_student_submission_status_email 
ON student_submission_status(student_email);

-- === Phase 4: 기존 컬럼 정리 (데이터 검증 후 실행) ===

-- 4-1. 기존 ID 기반 컬럼 삭제 (email 데이터 정상 확인 후)
-- ALTER TABLE submission DROP COLUMN class_member_id;
-- ALTER TABLE student_submission_status DROP COLUMN student_id;
