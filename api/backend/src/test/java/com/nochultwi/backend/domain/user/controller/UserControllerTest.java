package com.nochultwi.backend.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nochultwi.backend.domain.user.dto.FindIdRequestDto;
import com.nochultwi.backend.domain.user.dto.LoginRequestDto;
import com.nochultwi.backend.domain.user.dto.ResetPasswordRequestDto;
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
import static org.mockito.BDDMockito.willDoNothing;
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
        given(userService.login("student123", "password123!"))
                .willReturn("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.mockAccessToken...");

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
                                fieldWithPath("tokenType").description("토큰 인증 타입 (Bearer)"),
                                fieldWithPath("accessToken").description("발급된 JWT Access Token (자동 로그인)")
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

    @Test
    @DisplayName("아이디 찾기 API 문서화")
    void findId_성공() throws Exception {
        // given
        FindIdRequestDto requestDto = new FindIdRequestDto("student@example.com", 2024123456L);

        given(userService.findId("student@example.com", 2024123456L))
                .willReturn("student123");

        // when & then
        mockMvc.perform(post("/api/v1/users/findid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andDo(document("user-find-id",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("email").description("가입 시 등록한 이메일"),
                                fieldWithPath("studentNumber").description("학번")
                        ),
                        responseFields(
                                fieldWithPath("loginId").description("조회된 사용자 로그인 아이디")
                        )
                ));
    }

    @Test
    @DisplayName("비밀번호 재설정 API 문서화")
    void resetPassword_성공() throws Exception {
        // given
        ResetPasswordRequestDto requestDto = new ResetPasswordRequestDto(
                "student123",
                "student@example.com",
                2024123456L,
                "newPassword123!"
        );

        willDoNothing().given(userService).resetPassword(any(), any(), any(), any());

        // when & then
        mockMvc.perform(post("/api/v1/users/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andDo(document("user-reset-password",
                        preprocessRequest(prettyPrint()),
                        preprocessResponse(prettyPrint()),
                        requestFields(
                                fieldWithPath("loginId").description("사용자 로그인 아이디"),
                                fieldWithPath("email").description("가입 시 등록한 이메일"),
                                fieldWithPath("studentNumber").description("학번"),
                                fieldWithPath("newPassword").description("새로 설정할 비밀번호")
                        ),
                        responseFields(
                                fieldWithPath("message").description("처리 완료 안내 메시지")
                        )
                ));
    }
}
