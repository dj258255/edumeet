-- 요약본 MD/PDF URL 을 각각 저장한다. (#27)
--
-- 기존에는 s3url 단일 컬럼이라 PDF 가 MD 를 덮어썼다.
-- MD URL 은 HTTP 응답에만 담겨 있어서 나중에 조회할 방법이 없었다.
--
-- s3url 은 기존 조회 코드와의 호환을 위해 남긴다.
-- PDF 가 있으면 PDF, 없으면 MD 를 담는다.

ALTER TABLE meeting
    ADD COLUMN summary_md_url  VARCHAR(500) NULL COMMENT '요약본 Markdown S3 URL',
    ADD COLUMN summary_pdf_url VARCHAR(500) NULL COMMENT '요약본 PDF S3 URL';

-- 기존 데이터는 어느 형식인지 URL 확장자로만 판별할 수 있다.
UPDATE meeting SET summary_pdf_url = s3url WHERE s3url LIKE '%.pdf';
UPDATE meeting SET summary_md_url  = s3url WHERE s3url LIKE '%.md';
