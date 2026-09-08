package com.company.seqdiagram.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Creates the schema on first start. Missing objects are created and existing
 * ones are left untouched, so this is safe to run on every boot.
 */
@Configuration
@ConditionalOnProperty(name = "app.db.auto-init", havingValue = "true", matchIfMissing = true)
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private static final String CREATE_MEMBER = """
            CREATE TABLE MEMBER (
                ID          NUMBER(19)         NOT NULL,
                USERNAME    VARCHAR2(50 CHAR)  NOT NULL,
                PASSWORD    VARCHAR2(100 CHAR) NOT NULL,
                NAME        VARCHAR2(100 CHAR) NOT NULL,
                EMPLOYEE_NO VARCHAR2(50 CHAR)  NOT NULL,
                CREATED_AT  TIMESTAMP          DEFAULT SYSTIMESTAMP NOT NULL,
                CONSTRAINT PK_MEMBER PRIMARY KEY (ID),
                CONSTRAINT UK_MEMBER_USERNAME UNIQUE (USERNAME),
                CONSTRAINT UK_MEMBER_EMPLOYEE_NO UNIQUE (EMPLOYEE_NO)
            )
            """;

    private static final String CREATE_MEMBER_SESSION = """
            CREATE TABLE MEMBER_SESSION (
                TOKEN_HASH VARCHAR2(64 CHAR) NOT NULL,
                MEMBER_ID  NUMBER(19)        NOT NULL,
                CREATED_AT TIMESTAMP         DEFAULT SYSTIMESTAMP NOT NULL,
                EXPIRES_AT TIMESTAMP         NOT NULL,
                CONSTRAINT PK_MEMBER_SESSION PRIMARY KEY (TOKEN_HASH),
                CONSTRAINT FK_SESSION_MEMBER FOREIGN KEY (MEMBER_ID) REFERENCES MEMBER (ID)
            )
            """;

    private static final String CREATE_DIAGRAM = """
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
            )
            """;

    @Bean
    public ApplicationRunner seqDiagramSchemaInitializer(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                if (!tableExists(jdbcTemplate, "MEMBER")) {
                    jdbcTemplate.execute(CREATE_MEMBER);
                    log.info("Created table MEMBER");
                }
                if (!sequenceExists(jdbcTemplate, "MEMBER_SEQ")) {
                    jdbcTemplate.execute(
                            "CREATE SEQUENCE MEMBER_SEQ START WITH 1 INCREMENT BY 1 NOCACHE");
                    log.info("Created sequence MEMBER_SEQ");
                }
                if (!tableExists(jdbcTemplate, "MEMBER_SESSION")) {
                    jdbcTemplate.execute(CREATE_MEMBER_SESSION);
                    jdbcTemplate.execute(
                            "CREATE INDEX IX_SESSION_MEMBER ON MEMBER_SESSION (MEMBER_ID)");
                    log.info("Created table MEMBER_SESSION");
                }

                if (!tableExists(jdbcTemplate, "SEQ_DIAGRAM")) {
                    jdbcTemplate.execute(CREATE_DIAGRAM);
                    jdbcTemplate.execute(
                            "CREATE INDEX IX_SEQ_DIAGRAM_MEMBER ON SEQ_DIAGRAM (MEMBER_ID, UPDATED_AT DESC)");
                    log.info("Created table SEQ_DIAGRAM");
                } else if (!columnExists(jdbcTemplate, "SEQ_DIAGRAM", "MEMBER_ID")) {
                    // Upgrade from the pre-membership schema. The column stays
                    // nullable because existing rows have no owner yet; assign
                    // them with an UPDATE and they become visible again.
                    jdbcTemplate.execute("ALTER TABLE SEQ_DIAGRAM ADD MEMBER_ID NUMBER(19)");
                    jdbcTemplate.execute(
                            "CREATE INDEX IX_SEQ_DIAGRAM_MEMBER ON SEQ_DIAGRAM (MEMBER_ID, UPDATED_AT DESC)");
                    log.warn("Added SEQ_DIAGRAM.MEMBER_ID. Existing rows have no owner and will "
                            + "not be listed until MEMBER_ID is set for them.");
                }

                if (!sequenceExists(jdbcTemplate, "SEQ_DIAGRAM_SEQ")) {
                    jdbcTemplate.execute(
                            "CREATE SEQUENCE SEQ_DIAGRAM_SEQ START WITH 1 INCREMENT BY 1 NOCACHE");
                    log.info("Created sequence SEQ_DIAGRAM_SEQ");
                }
            } catch (Exception e) {
                // A read-only account is a valid setup: log it and let the DBA apply
                // backend/src/main/resources/db/oracle-schema.sql by hand.
                log.warn("Schema auto-initialisation skipped: {}. "
                        + "Apply db/oracle-schema.sql manually if the objects are missing.",
                        e.getMessage());
            }
        };
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String name) {
        return exists(jdbcTemplate, "SELECT COUNT(*) FROM USER_TABLES WHERE TABLE_NAME = ?", name);
    }

    private boolean sequenceExists(JdbcTemplate jdbcTemplate, String name) {
        return exists(jdbcTemplate,
                "SELECT COUNT(*) FROM USER_SEQUENCES WHERE SEQUENCE_NAME = ?", name);
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate, String table, String column) {
        return exists(jdbcTemplate,
                "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
                table, column);
    }

    private boolean exists(JdbcTemplate jdbcTemplate, String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count != null && count > 0;
    }
}
