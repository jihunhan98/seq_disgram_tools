-- ---------------------------------------------------------------------------
-- Schema for the sequence diagram tool.
-- Applied automatically at startup when app.db.auto-init is true; this file is
-- also the reference for creating the objects by hand.
-- ---------------------------------------------------------------------------

-- 회원 -----------------------------------------------------------------------
CREATE TABLE MEMBER (
    ID          NUMBER(19)         NOT NULL,
    USERNAME    VARCHAR2(50 CHAR)  NOT NULL,
    PASSWORD    VARCHAR2(100 CHAR) NOT NULL,  -- BCrypt 해시
    NAME        VARCHAR2(100 CHAR) NOT NULL,
    EMPLOYEE_NO VARCHAR2(50 CHAR)  NOT NULL,
    CREATED_AT  TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_MEMBER PRIMARY KEY (ID),
    CONSTRAINT UK_MEMBER_USERNAME UNIQUE (USERNAME),
    CONSTRAINT UK_MEMBER_EMPLOYEE_NO UNIQUE (EMPLOYEE_NO)
);

CREATE SEQUENCE MEMBER_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;

-- 로그인 세션 (토큰은 SHA-256 해시로만 보관) --------------------------------
CREATE TABLE MEMBER_SESSION (
    TOKEN_HASH VARCHAR2(64 CHAR) NOT NULL,
    MEMBER_ID  NUMBER(19)        NOT NULL,
    CREATED_AT TIMESTAMP         DEFAULT SYSTIMESTAMP NOT NULL,
    EXPIRES_AT TIMESTAMP         NOT NULL,
    CONSTRAINT PK_MEMBER_SESSION PRIMARY KEY (TOKEN_HASH),
    CONSTRAINT FK_SESSION_MEMBER FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (ID)
);

CREATE INDEX IX_SESSION_MEMBER ON MEMBER_SESSION (MEMBER_ID);

-- 시퀀스 다이어그램 (회원별) -------------------------------------------------
CREATE TABLE SEQ_DIAGRAM (
    ID           NUMBER(19)          NOT NULL,
    MEMBER_ID    NUMBER(19)          NOT NULL,
    TITLE        VARCHAR2(200 CHAR)  NOT NULL,
    DESCRIPTION  VARCHAR2(2000 CHAR),
    MERMAID_CODE CLOB                NOT NULL,
    LAST_PROMPT  VARCHAR2(4000 CHAR),
    CREATED_AT   TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATED_AT   TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_SEQ_DIAGRAM PRIMARY KEY (ID),
    CONSTRAINT FK_DIAGRAM_MEMBER FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (ID)
);

CREATE SEQUENCE SEQ_DIAGRAM_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE INDEX IX_SEQ_DIAGRAM_MEMBER ON SEQ_DIAGRAM (MEMBER_ID, UPDATED_AT DESC);

-- ---------------------------------------------------------------------------
-- 회원 기능 이전에 만들어진 SEQ_DIAGRAM 이 이미 있다면 아래로 이관합니다.
-- MEMBER_ID 가 비어 있는 행은 어느 회원에게도 보이지 않으므로 주인을 지정해야 합니다.
-- ---------------------------------------------------------------------------
-- ALTER TABLE SEQ_DIAGRAM ADD MEMBER_ID NUMBER(19);
-- UPDATE SEQ_DIAGRAM SET MEMBER_ID = (SELECT ID FROM MEMBER WHERE USERNAME = 'someone')
--  WHERE MEMBER_ID IS NULL;
-- ALTER TABLE SEQ_DIAGRAM MODIFY MEMBER_ID NUMBER(19) NOT NULL;
-- ALTER TABLE SEQ_DIAGRAM ADD CONSTRAINT FK_DIAGRAM_MEMBER
--     FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (ID);
