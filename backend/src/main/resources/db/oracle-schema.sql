-- ---------------------------------------------------------------------------
-- Schema for the sequence diagram tool.
-- Applied automatically at startup when app.db.auto-init is true; this file is
-- also the reference for creating the objects by hand.
-- ---------------------------------------------------------------------------

CREATE TABLE SEQ_DIAGRAM (
    ID           NUMBER(19)          NOT NULL,
    TITLE        VARCHAR2(200 CHAR)  NOT NULL,
    DESCRIPTION  VARCHAR2(2000 CHAR),
    MERMAID_CODE CLOB                NOT NULL,
    LAST_PROMPT  VARCHAR2(4000 CHAR),
    CREATED_AT   TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATED_AT   TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT PK_SEQ_DIAGRAM PRIMARY KEY (ID)
);

CREATE SEQUENCE SEQ_DIAGRAM_SEQ START WITH 1 INCREMENT BY 1 NOCACHE;

CREATE INDEX IX_SEQ_DIAGRAM_UPDATED ON SEQ_DIAGRAM (UPDATED_AT DESC);
