package com.company.seqdiagram.repository;

import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.company.seqdiagram.domain.Diagram;

/**
 * Plain JDBC access to SEQ_DIAGRAM. Kept explicit (rather than JPA) so the SQL
 * stays portable across the Oracle versions the tool may be pointed at, and so
 * CLOB handling is under our control.
 */
@Repository
public class DiagramRepository {

    private static final String COLUMNS =
            "ID, TITLE, DESCRIPTION, MERMAID_CODE, LAST_PROMPT, CREATED_AT, UPDATED_AT";

    private final JdbcTemplate jdbcTemplate;

    public DiagramRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Diagram> rowMapper = (ResultSet rs, int rowNum) -> new Diagram(
            rs.getLong("ID"),
            rs.getString("TITLE"),
            rs.getString("DESCRIPTION"),
            rs.getString("MERMAID_CODE"),
            rs.getString("LAST_PROMPT"),
            toOffsetDateTime(rs.getTimestamp("CREATED_AT")),
            toOffsetDateTime(rs.getTimestamp("UPDATED_AT")));

    /** Summary rows skip MERMAID_CODE so the list endpoint stays light. */
    private final RowMapper<Diagram> summaryRowMapper = (ResultSet rs, int rowNum) -> new Diagram(
            rs.getLong("ID"),
            rs.getString("TITLE"),
            rs.getString("DESCRIPTION"),
            null,
            null,
            toOffsetDateTime(rs.getTimestamp("CREATED_AT")),
            toOffsetDateTime(rs.getTimestamp("UPDATED_AT")));

    public List<Diagram> findAllSummaries() {
        return jdbcTemplate.query(
                "SELECT ID, TITLE, DESCRIPTION, CREATED_AT, UPDATED_AT "
                        + "FROM SEQ_DIAGRAM ORDER BY UPDATED_AT DESC, ID DESC",
                summaryRowMapper);
    }

    public Optional<Diagram> findById(long id) {
        List<Diagram> rows = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM SEQ_DIAGRAM WHERE ID = ?",
                rowMapper,
                id);
        return rows.stream().findFirst();
    }

    public long insert(String title, String description, String mermaidCode, String lastPrompt) {
        Long id = jdbcTemplate.queryForObject("SELECT SEQ_DIAGRAM_SEQ.NEXTVAL FROM DUAL", Long.class);
        if (id == null) {
            throw new IllegalStateException("SEQ_DIAGRAM_SEQ returned no value");
        }

        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO SEQ_DIAGRAM "
                            + "(ID, TITLE, DESCRIPTION, MERMAID_CODE, LAST_PROMPT, CREATED_AT, UPDATED_AT) "
                            + "VALUES (?, ?, ?, ?, ?, SYSTIMESTAMP, SYSTIMESTAMP)");
            ps.setLong(1, id);
            ps.setString(2, title);
            ps.setString(3, description);
            ps.setCharacterStream(4, new StringReader(mermaidCode), mermaidCode.length());
            ps.setString(5, lastPrompt);
            return ps;
        });

        return id;
    }

    /** Overwrites the stored diagram; earlier content is not retained by design. */
    public int update(long id, String title, String description, String mermaidCode, String lastPrompt) {
        return jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(
                    "UPDATE SEQ_DIAGRAM SET TITLE = ?, DESCRIPTION = ?, MERMAID_CODE = ?, "
                            + "LAST_PROMPT = ?, UPDATED_AT = SYSTIMESTAMP WHERE ID = ?");
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setCharacterStream(3, new StringReader(mermaidCode), mermaidCode.length());
            ps.setString(4, lastPrompt);
            ps.setLong(5, id);
            return ps;
        });
    }

    public int deleteById(long id) {
        return jdbcTemplate.update("DELETE FROM SEQ_DIAGRAM WHERE ID = ?", id);
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
