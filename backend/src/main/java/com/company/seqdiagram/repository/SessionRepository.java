package com.company.seqdiagram.repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Login sessions. Only the SHA-256 hash of a token is stored, so a leaked
 * database dump cannot be replayed as a login.
 */
@Repository
public class SessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public SessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(String tokenHash, long memberId, int validHours) {
        // The expiry is computed here rather than with a database interval
        // expression, which keeps the SQL portable.
        Timestamp expiresAt = Timestamp.from(Instant.now().plus(Duration.ofHours(validHours)));

        jdbcTemplate.update(
                "INSERT INTO MEMBER_SESSION (TOKEN_HASH, MEMBER_ID, CREATED_AT, EXPIRES_AT) "
                        + "VALUES (?, ?, SYSTIMESTAMP, ?)",
                tokenHash, memberId, expiresAt);
    }

    public Optional<Long> findMemberId(String tokenHash) {
        return jdbcTemplate.query(
                "SELECT MEMBER_ID FROM MEMBER_SESSION WHERE TOKEN_HASH = ? AND EXPIRES_AT > ?",
                (rs, rowNum) -> rs.getLong("MEMBER_ID"),
                tokenHash, Timestamp.from(Instant.now())).stream().findFirst();
    }

    public void delete(String tokenHash) {
        jdbcTemplate.update("DELETE FROM MEMBER_SESSION WHERE TOKEN_HASH = ?", tokenHash);
    }

    /** Called on login so expired rows do not accumulate. */
    public int deleteExpired() {
        return jdbcTemplate.update(
                "DELETE FROM MEMBER_SESSION WHERE EXPIRES_AT <= ?", Timestamp.from(Instant.now()));
    }
}
