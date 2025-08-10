# EduMeet Homework 시스템 API 명세서 (Updated)

## 📋 시스템 개요

EduMeet Homework 시스템은 온라인 교육 플랫폼에서 과제 관리를 위한 완전한 솔루션입니다.

### 주요 기능
- 과제 생성, 수정, 삭제, 조회
- 과제 제출물 관리 (제출, 삭제) - **제출물 수정 기능 제거**
- **S3 Presigned URL을 통한 파일 업로드**
- 제출 현황 추적 (실시간 제출 상태 관리)
- 논리적 삭제 및 복원 기능
- 클래스별, 학생별 데이터 조회
- **클래스 기반 API 구조**

### 사용 기술 스택
- **Backend Framework**: Spring Boot 3.x
- **Database**: JPA/Hibernate with MySQL
- **File Storage**: AWS S3 with Presigned URLs
- **Architecture**: Clean Architecture (Domain-Driven Design)
  - Domain Layer: 비즈니스 로직
  - Application Layer: 애플리케이션 서비스
  - Infrastructure Layer: 데이터 저장소
  - Presentation Layer: REST API 컨트롤러
- **Documentation**: Swagger/OpenAPI 3
- **Logging**: Log4j2
- **Validation**: Bean Validation (JSR-303)

---

## 🎯 Assignment (과제) API

### Base URL: `/api/v1/class/{classId}/assignments`

#### 1. 과제 생성
```http
POST /api/v1/class/{classId}/assignments
```

**Request Body:**
```json
{
  "title": "Spring Boot 실습 과제",
  "description": "Spring Boot를 활용한 REST API 개발",
  "classId": 1,
  "createdById": 10,
  "createdByName": "김선생",
  "attachmentFiles": [
    {
      "uuid": "abc-123-def-456",
      "fileName": "assignment_guide.pdf",
      "ord": 1,
      "img": false,
      "fileSize": 1024000,
      "contentType": "application/pdf",
      "uploadedBy": "김선생",
      "referenceId": 1234
    }
  ]
}
```

**Response:**
```
201 Created
1234
```

#### 2. 과제 조회
```http
GET /api/v1/class/{classId}/assignments/{id}
```

**Response:**
```json
{
  "id": 1234,
  "title": "Spring Boot 실습 과제",
  "description": "Spring Boot를 활용한 REST API 개발",
  "classId": 1,
  "createdById": 10,
  "createdByName": "김선생",
  "attachmentFiles": [
    {
      "uuid": "abc-123-def-456",
      "fileName": "assignment_guide.pdf",
      "ord": 1,
      "img": false,
      "fileSize": 1024000,
      "contentType": "application/pdf",
      "uploadedBy": "김선생",
      "referenceId": 1234,
      "uploadedAt": "2025-08-10T17:00:00"
    }
  ],
  "studentSubmissionStatuses": [
    {
      "assignmentId": 1234,
      "studentId": 20,
      "studentName": "김학생",
      "status": "NOT_SUBMITTED",
      "submittedAt": null
    }
  ],
  "regDate": "2025-08-10T17:02:00",
  "modDate": "2025-08-10T17:02:00"
}
```

#### 3. 과제 수정
```http
PUT /api/v1/class/{classId}/assignments/{id}
```

**Request Body:**
```json
{
  "title": "수정된 과제 제목",
  "description": "수정된 과제 설명",
  "updaterId": 10
}
```

#### 4. 과제 삭제 (논리적 삭제)
```http
DELETE /api/v1/class/{classId}/assignments/{id}
```

#### 5. 클래스별 과제 목록 조회
```http
GET /api/v1/class/{classId}/assignments
```

**Response:**
```json
[
  {
    "id": 1234,
    "title": "Spring Boot 실습 과제",
    "description": "Spring Boot를 활용한 REST API 개발",
    "classId": 1,
    "createdById": 10,
    "createdByName": "김선생",
    "regDate": "2025-08-10T17:02:00",
    "modDate": "2025-08-10T17:02:00"
  }
]
```

#### 6. 과제 첨부파일 Presigned URL 생성 ⭐ NEW
```http
POST /api/v1/class/{classId}/assignments/{id}/presigned-url?fileName=assignment_guide.pdf
```

**Response:**
```json
{
  "presignedUrl": "https://bucket.s3.amazonaws.com/assignments/uuid_assignment_guide.pdf?X-Amz-...",
  "uuid": "generated-uuid",
  "fileName": "assignment_guide.pdf"
}
```

#### 7. 과제 첨부파일 추가
```http
POST /api/v1/class/{classId}/assignments/{id}/files
```

**Request Body:**
```json
{
  "uuid": "uuid-string",
  "fileName": "assignment_guide.pdf",
  "ord": 1,
  "img": false,
  "domain": "assignments",
  "referenceId": 1234
}
```

> **⚠️ 주요 변경사항**: 이제 `FileUploadDTO`를 사용합니다. `fileSize`, `contentType`, `uploadedBy` 필드는 제거되고, `domain` 필드가 추가되었습니다. 이는 기존 upload 시스템과의 통합을 위한 변경입니다.

#### 8. 첨부파일 포함 과제 조회
```http
GET /api/v1/class/{classId}/assignments/{id}/with-files
```

#### 9. 제출 현황 포함 과제 조회
```http
GET /api/v1/class/{classId}/assignments/{id}/with-submissions
```

#### 10. 과제 복원
```http
POST /api/v1/class/{classId}/assignments/{id}/restore
```

---

## 📝 Submission (제출물) API

### Base URL: `/api/v1/class/{classId}/submissions`

#### 1. 과제 제출
```http
POST /api/v1/class/{classId}/submissions
```

**Request Body:**
```json
{
  "assignmentId": 1234,
  "classMemberId": 20,
  "classMemberName": "김학생",
  "content": "과제를 완료했습니다. 첨부파일을 확인해주세요."
}
```

**Response:**
```
201 Created
5678
```

#### 2. 제출물 조회
```http
GET /api/v1/class/{classId}/submissions/{id}
```

**Response:**
```json
{
  "id": 5678,
  "assignmentId": 1234,
  "classMemberId": 20,
  "classMemberName": "김학생",
  "content": "과제를 완료했습니다.",
  "status": "SUBMITTED",
  "submissionFiles": [],
  "regDate": "2025-08-10T18:00:00",
  "modDate": "2025-08-10T18:00:00"
}
```

#### 3. 제출물 삭제
```http
DELETE /api/v1/class/{classId}/submissions/{id}
```

**⚠️ 제출물 수정 기능 제거됨 - 학생은 제출 한번만 가능**

#### 4. 과제별 제출물 목록 조회
```http
GET /api/v1/class/{classId}/submissions/assignment/{assignmentId}
```

#### 5. 학생별 제출물 목록 조회
```http
GET /api/v1/class/{classId}/submissions/class-member/{classMemberId}
```

#### 6. 특정 과제의 특정 학생 제출물 조회
```http
GET /api/v1/class/{classId}/submissions/assignment/{assignmentId}/class-member/{classMemberId}
```

#### 7. 제출물 파일 Presigned URL 생성 ⭐ NEW
```http
POST /api/v1/class/{classId}/submissions/{id}/presigned-url?fileName=homework.pdf
```

**Response:**
```json
{
  "presignedUrl": "https://bucket.s3.amazonaws.com/submissions/uuid_homework.pdf?X-Amz-...",
  "uuid": "generated-uuid",
  "fileName": "homework.pdf"
}
```

#### 8. 제출물 파일 추가
```http
POST /api/v1/class/{classId}/submissions/{id}/files
```

**Request Body:**
```json
{
  "uuid": "uuid-string",
  "fileName": "homework.pdf",
  "ord": 1,
  "img": false,
  "domain": "submissions",
  "referenceId": 5678
}
```

> **⚠️ 주요 변경사항**: 이제 `FileUploadDTO`를 사용합니다. 기존 upload 시스템과의 통합으로 더 일관된 파일 관리가 가능합니다.

#### 9. 첨부파일 포함 제출물 조회
```http
GET /api/v1/class/{classId}/submissions/{id}/with-files
```

#### 10. 제출물 복원
```http
POST /api/v1/class/{classId}/submissions/{id}/restore
```

---

## 🚀 파일 업로드 프로세스 ⭐ NEW

### S3 Presigned URL을 통한 파일 업로드

#### 1. 과제 첨부파일 업로드
```
1. POST /api/v1/class/{classId}/assignments/{id}/presigned-url?fileName=guide.pdf
   → presigned URL 받기
   
2. PUT {presignedUrl} (클라이언트에서 S3로 직접 업로드)
   → 파일을 S3에 직접 업로드
   
3. POST /api/v1/class/{classId}/assignments/{id}/files
   → 업로드 완료 후 파일 정보를 시스템에 등록
```

#### 2. 제출물 파일 업로드
```
1. POST /api/v1/class/{classId}/submissions/{id}/presigned-url?fileName=homework.pdf
   → presigned URL 받기
   
2. PUT {presignedUrl} (클라이언트에서 S3로 직접 업로드)
   → 파일을 S3에 직접 업로드
   
3. POST /api/v1/class/{classId}/submissions/{id}/files
   → 업로드 완료 후 파일 정보를 시스템에 등록
```

### 장점
- **보안**: 서버를 거치지 않고 직접 S3 업로드
- **성능**: 대용량 파일 업로드 시 서버 부하 없음
- **확장성**: AWS의 무제한 스토리지 활용

---

## 📊 데이터 모델

### Assignment (과제)
- **id**: 과제 고유 ID
- **title**: 과제 제목
- **description**: 과제 설명
- **classId**: 클래스 ID ⭐ 모든 API 경로에 포함
- **createdById**: 생성자 ID
- **createdByName**: 생성자 이름
- **attachmentFiles**: 첨부파일 목록
- **studentSubmissionStatuses**: 학생별 제출 현황
- **regDate**: 등록일시
- **modDate**: 수정일시
- **deletedAt**: 삭제일시 (논리적 삭제)

### Submission (제출물)
- **id**: 제출물 고유 ID
- **assignmentId**: 과제 ID
- **classMemberId**: 클래스 멤버 ID
- **classMemberName**: 클래스 멤버 이름
- **content**: 제출물 내용
- **status**: 제출 상태 (NOT_SUBMITTED, SUBMITTED)
- **submissionFiles**: 제출 파일 목록
- **regDate**: 등록일시
- **modDate**: 수정일시 (수정 불가능)
- **deletedAt**: 삭제일시

### SubmissionStatus (제출 상태)
- **NOT_SUBMITTED**: 미제출
- **SUBMITTED**: 제출완료 (수정 불가능)

---

## 🔐 보안 및 권한

### 과제 관리 권한
- 과제 생성: 강사만 가능
- 과제 수정: 과제 생성자만 가능
- 과제 삭제: 과제 생성자만 가능

### 제출물 관리 권한
- 제출물 생성: 클래스 멤버만 가능
- **제출물 수정: 불가능 (한번 제출하면 끝)**
- 제출물 삭제: 제출자만 가능

### 파일 업로드 권한
- Presigned URL 생성: 해당 과제/제출물의 권한이 있는 사용자만
- 파일 업로드: 10분 제한시간 내에만 가능

---

## ⚠️ 에러 응답

### 일반적인 에러 응답
```json
{
  "timestamp": "2025-08-10T17:02:00",
  "status": 400,
  "error": "Bad Request",
  "message": "해당 과제를 찾을 수 없습니다: 1234",
  "path": "/api/v1/class/1/assignments/1234"
}
```

### 주요 에러 코드
- **400 Bad Request**: 잘못된 요청 데이터
- **401 Unauthorized**: 인증되지 않은 사용자
- **403 Forbidden**: 권한 없음
- **404 Not Found**: 리소스를 찾을 수 없음
- **500 Internal Server Error**: 서버 내부 오류

---

## 📈 사용 시나리오

### 1. 강사가 과제를 생성하고 파일을 첨부하는 경우
```
1. POST /api/v1/class/1/assignments (과제 생성)
2. POST /api/v1/class/1/assignments/1234/presigned-url?fileName=guide.pdf
3. PUT {presignedUrl} (S3에 파일 업로드)
4. POST /api/v1/class/1/assignments/1234/files (파일 정보 등록)
5. GET /api/v1/class/1/assignments/1234/with-submissions (제출 현황 확인)
```

### 2. 학생이 과제를 제출하는 경우
```
1. GET /api/v1/class/1/assignments (과제 목록 확인)
2. GET /api/v1/class/1/assignments/1234 (과제 상세 확인)
3. POST /api/v1/class/1/submissions (과제 제출)
4. POST /api/v1/class/1/submissions/5678/presigned-url?fileName=homework.pdf
5. PUT {presignedUrl} (S3에 파일 업로드)
6. POST /api/v1/class/1/submissions/5678/files (파일 정보 등록)
```

### 3. 제출 현황을 관리하는 경우
```
1. GET /api/v1/class/1/assignments/1234/with-submissions (전체 제출 현황)
2. GET /api/v1/class/1/submissions/assignment/1234 (과제별 제출물)
3. GET /api/v1/class/1/submissions/class-member/20 (학생별 제출물)
```

---

## 🎪 특별 기능

### 실시간 제출 현황 추적
- 과제 생성시 클래스 멤버들의 제출 현황이 자동으로 초기화됩니다
- 학생이 과제를 제출하면 실시간으로 제출 현황이 업데이트됩니다

### 논리적 삭제
- 모든 삭제는 논리적 삭제로 처리되어 데이터 복원이 가능합니다
- 삭제된 데이터는 일반 조회에서 제외됩니다

### S3 Presigned URL 파일 관리 ⭐ NEW
- 안전하고 효율적인 파일 업로드
- 서버 부하 최소화
- AWS S3의 확장성 활용

### 클래스 기반 API 구조 ⭐ NEW
- 모든 API가 클래스 단위로 구성
- 과제 게시판이 클래스 안에 위치하는 구조 반영
- 클래스별 권한 관리 용이

### 제출 한번 정책 ⭐ NEW
- 학생은 과제를 한번만 제출 가능
- 제출물 수정 기능 완전 제거
- 신중한 제출 유도

이 시스템은 완전한 온라인 과제 관리 솔루션으로, 교육 기관에서 바로 사용할 수 있는 수준으로 구현되어 있습니다! 🎓✨