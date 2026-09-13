package com.nochultwi.backend.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nochultwi.backend.domain.user.dto.LoginRequestDto;
import com.nochultwi.backend.domain.user.dto.SignUpRequestDto;
import com.nochultwi.backend.domain.user.entity.Role;
import com.nochultwi.backend.domain.user.entity.User;
import com.nochultwi.backend.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.restdocs.RestDocumentationContextProvider;
import org.springframework.restdocs.RestDocumentationExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*;
import static org.springframework.restdocs.payload.PayloadDocumentation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(RestDocumentationExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;
    private UserService userService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp(RestDocumentationContextProvider restDocumentation) {
        this.userService = mock(UserService.class);
        UserController userController = new UserController(this.userService);

        this.mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .apply(documentationConfiguration(restDocumentation))
                .build();
    }

    @Test
    @DisplayName("회원가입 API 문서화")
    void signUp_성공() throws Exception {
        // given
        SignUpRequestDto requestDto = new SignUpRequestDto(
                "student123",
                "홍길동",
                "student@example.com",
                "password123!",
                2024123456L,
                Role.ROLE_STUDENT
        );

        User mockUser = User.builder()
                .id(1L)
                .loginId("student123")
                .password("password123!")
                .name("홍길동")
                .email("student@example.com")
                .studentNumber(2024123456L)
                .role(Role.ROLE_STUDENT)
                .build();

        given(userService.signUp(any(), any(), any(), any(), any(), any()))
                .willReturn(mockUser);

        // when & then
        mockMvc.perform(post("/api/v1/users/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andDo(document("user-signup",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("loginId").description("로그인 아이디"),
                                fieldWithPath("password").description("비밀번호"),
                                fieldWithPath("name").description("사용자 이름"),
                                fieldWithPath("email").description("이메일 주소"),
                                fieldWithPath("studentNumber").description("학번"),
                                fieldWithPath("role").description("사용자 역할 (ROLE_STUDENT / ROLE_ADMIN)")
                        ),
                        responseFields(
                                fieldWithPath("id").description("생성된 사용자 고유 ID"),
                                fieldWithPath("loginId").description("로그인 아이디"),
                                fieldWithPath("password").description("비밀번호 (해시값 또는 암호화된 값)"),
                                fieldWithPath("name").description("사용자 이름"),
                                fieldWithPath("email").description("이메일 주소"),
                                fieldWithPath("studentNumber").description("학번"),
                                fieldWithPath("role").description("사용자 역할")
                        )
                ));
    }

    @Test
    @DisplayName("로그인 API 문서화")
    void login_성공() throws Exception {
        // given
        LoginRequestDto requestDto = new LoginRequestDto("student123", "password123!");
        String mockToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.mockAccessToken...";

        given(userService.login("student123", "password123!"))
                .willReturn(mockToken);

        // when & then
        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andDo(document("user-login",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("loginId").description("로그인 아이디"),
                                fieldWithPath("password").description("비밀번호")
                        ),
                        responseFields(
                                fieldWithPath("tokenType").description("토큰 인증 타입 (Bearer)"),
                                fieldWithPath("accessToken").description("발급된 JWT Access Token")
                        )
                ));
    }
}
