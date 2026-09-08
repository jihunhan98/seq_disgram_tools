package com.company.seqdiagram.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "아이디를 입력하세요")
        @Size(min = 4, max = 50, message = "아이디는 4~50자여야 합니다")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$",
                message = "아이디는 영문, 숫자, . _ - 만 사용할 수 있습니다")
        String username,

        @NotBlank(message = "비밀번호를 입력하세요")
        @Size(min = 8, max = 72, message = "비밀번호는 8자 이상이어야 합니다")
        String password,

        @NotBlank(message = "이름을 입력하세요")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다")
        String name,

        @NotBlank(message = "사번을 입력하세요")
        @Size(max = 50, message = "사번은 50자 이하여야 합니다")
        String employeeNo) {
}
