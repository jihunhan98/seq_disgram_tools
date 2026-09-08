package com.company.seqdiagram.domain;

import java.time.OffsetDateTime;

/** A registered user. The password hash never leaves the repository layer. */
public record Member(
        Long id,
        String username,
        String name,
        String employeeNo,
        OffsetDateTime createdAt) {
}
