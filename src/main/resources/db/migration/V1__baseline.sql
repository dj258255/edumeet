-- V1 — 기존 스키마 baseline (#29)
--
-- 이 파일은 손으로 쓰지 않았다. 엔티티에서 생성했다:
--   ./gradlew test --tests '*SchemaExportTest'   →  build/schema-mysql.sql
--
-- 운영 DB 에는 이미 이 스키마가 있다. Flyway 는 baseline-on-migrate 로
-- 기존 DB 를 V1 으로 표시하고 이 파일을 실행하지 않는다.
-- 빈 DB(신규 로컬 개발자)에서만 실제로 실행된다.
--
-- 따라서 이 파일은 "운영 스키마의 사본" 이 아니라 "엔티티가 기대하는 스키마" 다.
-- 둘이 다르면 V2 이후 마이그레이션에서 드러난다.

create table assignment (
    class_id bigint not null,
    deleted_at datetime(6),
    id bigint not null auto_increment,
    mod_date datetime(6),
    reg_date datetime(6),
    title varchar(200) not null,
    created_by_email varchar(255),
    created_by_name varchar(255),
    description TEXT,
    primary key (id)
) engine=InnoDB;
create table assignment_file_upload (
    img bit not null,
    ord integer not null,
    assignment_id bigint,
    deleted_at datetime(6),
    file_size bigint,
    id bigint not null auto_increment,
    mod_date datetime(6),
    reference_id bigint,
    reg_date datetime(6),
    content_type varchar(255),
    domain varchar(255),
    file_name varchar(255) not null,
    uploaded_by varchar(255),
    uuid varchar(255) not null,
    primary key (id)
) engine=InnoDB;
create table board (
    category_id bigint,
    class_id bigint not null,
    deleted_at datetime(6),
    dislike BIGINT DEFAULT 0,
    favorite BIGINT DEFAULT 0,
    id bigint not null auto_increment,
    mod_date datetime(6),
    reg_date datetime(6),
    view BIGINT DEFAULT 0,
    title varchar(50) not null,
    writer varchar(100) not null,
    content TEXT,
    board_type VARCHAR(20) DEFAULT 'NORMAL',
    primary key (id)
) engine=InnoDB;
create table board_category (
    is_active bit not null,
    recommend_threshold integer not null,
    sort_order integer not null,
    class_id bigint not null,
    deleted_at datetime(6),
    id bigint not null auto_increment,
    mod_date datetime(6),
    parent_id bigint,
    reg_date datetime(6),
    category_name varchar(255) not null,
    created_by varchar(255) not null,
    description varchar(255),
    primary key (id)
) engine=InnoDB;
create table board_file_upload (
    img bit not null,
    ord integer not null,
    board_id bigint,
    deleted_at datetime(6),
    file_size bigint,
    id bigint not null auto_increment,
    mod_date datetime(6),
    reference_id bigint,
    reg_date datetime(6),
    content_type varchar(255),
    file_name varchar(255) not null,
    uploaded_by varchar(255),
    uuid varchar(255) not null,
    primary key (id)
) engine=InnoDB;
create table class_invite (
    class_room_id bigint,
    deleted_at datetime(6),
    id bigint not null auto_increment,
    invitee_id bigint,
    mod_date datetime(6),
    reg_date datetime(6),
    status enum ('ACCEPTED','APPLIED','DENIED'),
    primary key (id)
) engine=InnoDB;
create table class_member (
    class_room_id bigint,
    id bigint not null auto_increment,
    member_id bigint,
    primary key (id)
) engine=InnoDB;
create table class_room (
    is_deleted BOOLEAN DEFAULT FALSE not null,
    participant_limit integer,
    class_room_id bigint not null,
    deleted_at datetime(6),
    member_id bigint,
    mod_date datetime(6),
    reg_date datetime(6),
    description varchar(255),
    original_url varchar(255),
    thumbnail_url varchar(255),
    thumbnail_uuid varchar(255),
    title varchar(255),
    primary key (class_room_id)
) engine=InnoDB;
create table class_room_seq (
    next_val bigint
) engine=InnoDB;
insert into class_room_seq values ( 1 );
create table class_room_tags (
    class_room_class_room_id bigint,
    id bigint not null auto_increment,
    name varchar(255),
    primary key (id)
) engine=InnoDB;
create table meeting (
    class_room_id bigint not null,
    end_time datetime(6),
    id bigint not null auto_increment,
    start_time datetime(6) not null,
    title varchar(100) not null,
    description varchar(500),
    s3url varchar(255),
    session_type enum ('BROADCAST','INTERACTIVE') not null,
    primary key (id)
) engine=InnoDB;
create table meeting_participant (
    id bigint not null auto_increment,
    joined_at datetime(6) not null,
    left_at datetime(6),
    meeting_id bigint not null,
    participant_email varchar(100) not null,
    primary key (id)
) engine=InnoDB;
create table member (
    deleted_at datetime(6),
    id bigint not null auto_increment,
    mod_date datetime(6),
    reg_date datetime(6),
    nickname varchar(50) not null,
    email varchar(100) not null,
    password varchar(255) not null,
    provider varchar(255),
    provider_id varchar(255),
    primary key (id)
) engine=InnoDB;
create table refresh_token (
    expiration datetime(6) not null,
    id bigint not null auto_increment,
    member_id bigint not null,
    token varchar(512) not null,
    primary key (id)
) engine=InnoDB;
create table reply (
    depth integer not null,
    board_id bigint,
    deleted_at datetime(6),
    id bigint not null auto_increment,
    mod_date datetime(6),
    parent_id bigint,
    reg_date datetime(6),
    replayer varchar(255),
    reply_text varchar(255),
    primary key (id)
) engine=InnoDB;
create table student_submission_status (
    assignment_id bigint,
    id bigint not null auto_increment,
    submitted_at datetime(6),
    student_name varchar(50) not null,
    student_email varchar(255) not null,
    status enum ('NOT_SUBMITTED','SUBMITTED') not null,
    primary key (id)
) engine=InnoDB;
create table submission (
    assignment_id bigint not null,
    deleted_at datetime(6),
    id bigint not null auto_increment,
    mod_date datetime(6),
    reg_date datetime(6),
    class_member_email varchar(255),
    class_member_name varchar(255),
    content TEXT,
    status enum ('NOT_SUBMITTED','SUBMITTED') not null,
    primary key (id)
) engine=InnoDB;
create table submission_file_upload (
    img bit not null,
    ord integer not null,
    deleted_at datetime(6),
    file_size bigint,
    id bigint not null auto_increment,
    mod_date datetime(6),
    reference_id bigint,
    reg_date datetime(6),
    student_submission_status_id bigint,
    submission_id bigint,
    content_type varchar(255),
    domain varchar(255),
    file_name varchar(255) not null,
    uploaded_by varchar(255),
    uuid varchar(255) not null,
    primary key (id)
) engine=InnoDB;
alter table meeting_participant 
   add constraint uk_meeting_participant unique (meeting_id, participant_email);
alter table member 
   add constraint UKmbmcqelty0fbrvxp1q58dn57t unique (email);
alter table refresh_token 
   add constraint UKdnbbikqdsc2r2cee1afysqfk9 unique (member_id);
alter table refresh_token 
   add constraint UKr4k4edos30bx9neoq81mdvwph unique (token);
create index idx_reply_board_id 
   on reply (board_id);
create index idx_reply_parent_id 
   on reply (parent_id);
alter table assignment_file_upload 
   add constraint FK5ty4r79nyswusaaj4lqwmuw7a 
   foreign key (assignment_id) 
   references assignment (id);
alter table board_category 
   add constraint FKj33evch04jxdr3xhf0bbsw2ph 
   foreign key (parent_id) 
   references board_category (id);
alter table board_file_upload 
   add constraint FKjpungikwmtrfnundn9g7xw9jr 
   foreign key (board_id) 
   references board (id);
alter table class_invite 
   add constraint FKt3nfqig7sr5qoxv2c08bykc3l 
   foreign key (class_room_id) 
   references class_room (class_room_id);
alter table class_invite 
   add constraint FKaokt124mb9yjn1r73besqsbmt 
   foreign key (invitee_id) 
   references member (id);
alter table class_member 
   add constraint FKhrb31q1ndu0q3eaj00h2sn3eg 
   foreign key (class_room_id) 
   references class_room (class_room_id);
alter table class_member 
   add constraint FK6qigkm2yyoupyn32rijfpy52r 
   foreign key (member_id) 
   references member (id);
alter table class_room 
   add constraint FKjgcv37s2fs60bwfacv6cxdw4y 
   foreign key (member_id) 
   references member (id);
alter table class_room_tags 
   add constraint FK4be7yt2lvq8j0gmg70uhiwe6o 
   foreign key (class_room_class_room_id) 
   references class_room (class_room_id);
alter table meeting 
   add constraint FKoveticxd6hjq9pxdr2ux64x9u 
   foreign key (class_room_id) 
   references class_room (class_room_id);
alter table meeting_participant 
   add constraint FK26jnnkay5w0sko7x7kqu17xbr 
   foreign key (meeting_id) 
   references meeting (id);
alter table reply 
   add constraint FKcs9hiip0bv9xxfrgoj0lwv2dt 
   foreign key (board_id) 
   references board (id);
alter table reply 
   add constraint FK653lh98w5hv6139pt9s7ejyr8 
   foreign key (parent_id) 
   references reply (id);
alter table student_submission_status 
   add constraint FKp2ep48be4p4xo91l95uxi8o11 
   foreign key (assignment_id) 
   references assignment (id);
alter table submission_file_upload 
   add constraint FK23s8ls4quxa3v41ilm86yjeq6 
   foreign key (student_submission_status_id) 
   references student_submission_status (id);
alter table submission_file_upload 
   add constraint FK23s0w3tqv9y0bg0c5vocn9sp 
   foreign key (submission_id) 
   references submission (id);
