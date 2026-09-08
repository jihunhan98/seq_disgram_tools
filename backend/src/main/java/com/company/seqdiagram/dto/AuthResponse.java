package com.company.seqdiagram.dto;

import com.company.seqdiagram.domain.Member;

/** Issued on signup and login; the token goes in the Authorization header. */
public record AuthResponse(String token, Member member) {
}
