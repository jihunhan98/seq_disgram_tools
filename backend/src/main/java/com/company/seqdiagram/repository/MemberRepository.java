package com.company.seqdiagram.repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.company.seqdiagram.domain.Member;

@Repository
public class MemberRepository {

    private final JdbcTemplate jdbcTemplate;

    public MemberRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Member> rowMapper = (ResultSet rs, int rowNum) -> new Member(
            rs.getLong("ID"),
            rs.getString("USERNAME"),
            rs.getString("NAME"),
            rs.getString("EMPLOYEE_NO"),
            toOffsetDateTime(rs.getTimestamp("CREATED_AT")));

    public boolean usernameExists(String username) {
        return count("SELECT COUNT(*) FROM MEMBER WHERE LOWER(USERNAME) = LOWER(?)", username) > 0;
    }

    public boolean employeeNoExists(String employeeNo) {
        return count("SELECT COUNT(*) FROM MEMBER WHERE EMPLOYEE_NO = ?", employeeNo) > 0;
    }

    public long insert(String username, String passwordHash, String name, String employeeNo) {
        Long id = jdbcTemplate.queryForObject("SELECT MEMBER_SEQ.NEXTVAL FROM DUAL", Long.class);
        if (id == null) {
            throw new IllegalStateException("MEMBER_SEQ returned no value");
        }

        jdbcTemplate.update(
                "INSERT INTO MEMBER (ID, USERNAME, PASSWORD, NAME, EMPLOYEE_NO, CREATED_AT) "
                        + "VALUES (?, ?, ?, ?, ?, SYSTIMESTAMP)",
                id, username, passwordHash, name, employeeNo);
        return id;
    }

    public Optional<Member> findById(long id) {
        return first(jdbcTemplate.query(
                "SELECT ID, USERNAME, NAME, EMPLOYEE_NO, CREATED_AT FROM MEMBER WHERE ID = ?",
                rowMapper, id));
    }

    public Optional<Member> findByUsername(String username) {
        return first(jdbcTemplate.query(
                "SELECT ID, USERNAME, NAME, EMPLOYEE_NO, CREATED_AT FROM MEMBER "
                        + "WHERE LOWER(USERNAME) = LOWER(?)",
                rowMapper, username));
    }

    /** Kept separate from {@link #findByUsername} so hashes are read deliberately. */
    public Optional<String> findPasswordHash(String username) {
        return first(jdbcTemplate.query(
                "SELECT PASSWORD FROM MEMBER WHERE LOWER(USERNAME) = LOWER(?)",
                (rs, rowNum) -> rs.getString("PASSWORD"),
                username));
    }

    private int count(String sql, Object... args) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return count == null ? 0 : count;
    }

    private static <T> Optional<T> first(List<T> rows) {
        return rows.stream().findFirst();
    }

    private static OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
