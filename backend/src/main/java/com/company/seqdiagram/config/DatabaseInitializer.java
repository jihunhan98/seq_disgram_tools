package com.company.seqdiagram.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Creates SEQ_DIAGRAM and its sequence on first start. Missing objects are
 * created; existing ones are left untouched, so this is safe to run every time.
 */
@Configuration
@ConditionalOnProperty(name = "app.db.auto-init", havingValue = "true", matchIfMissing = true)
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private static final String CREATE_TABLE = """
            CREATE TABLE SEQ_DIAGRAM (
                ID           NUMBER(19)          NOT NULL,
                TITLE        VARCHAR2(200 CHAR)  NOT NULL,
                DESCRIPTION  VARCHAR2(2000 CHAR),
                MERMAID_CODE CLOB                NOT NULL,
                LAST_PROMPT  VARCHAR2(4000 CHAR),
                CREATED_AT   TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
                UPDATED_AT   TIMESTAMP           DEFAULT SYSTIMESTAMP NOT NULL,
                CONSTRAINT PK_SEQ_DIAGRAM PRIMARY KEY (ID)
            )
            """;

    private static final String CREATE_SEQUENCE =
            "CREATE SEQUENCE SEQ_DIAGRAM_SEQ START WITH 1 INCREMENT BY 1 NOCACHE";

    private static final String CREATE_INDEX =
            "CREATE INDEX IX_SEQ_DIAGRAM_UPDATED ON SEQ_DIAGRAM (UPDATED_AT DESC)";

    @Bean
    public ApplicationRunner seqDiagramSchemaInitializer(JdbcTemplate jdbcTemplate) {
        return args -> {
            try {
                if (!exists(jdbcTemplate, "USER_TABLES", "TABLE_NAME", "SEQ_DIAGRAM")) {
                    jdbcTemplate.execute(CREATE_TABLE);
                    jdbcTemplate.execute(CREATE_INDEX);
                    log.info("Created table SEQ_DIAGRAM");
                }
                if (!exists(jdbcTemplate, "USER_SEQUENCES", "SEQUENCE_NAME", "SEQ_DIAGRAM_SEQ")) {
                    jdbcTemplate.execute(CREATE_SEQUENCE);
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

    private boolean exists(JdbcTemplate jdbcTemplate, String dictionary, String column, String name) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + dictionary + " WHERE " + column + " = ?",
                Integer.class,
                name);
        return count != null && count > 0;
    }
}
