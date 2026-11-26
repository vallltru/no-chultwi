package com.nochultwi.backend.global;

import com.nochultwi.backend.global.error.BusinessException;
import com.nochultwi.backend.global.error.ErrorCode;
import com.nochultwi.backend.global.filter.RequestIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the global web/security baseline through the real filter chain
 * (RequestIdFilter -> Spring Security -> DispatcherServlet -> GlobalExceptionHandler).
 */
@SpringBootTest
@ActiveProfiles({"local", "test"})
@Import(GlobalWebSecurityIntegrationTest.ProbeController.class)
class GlobalWebSecurityIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private RequestIdFilter requestIdFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(requestIdFilter)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("인증 없는 요청은 401 UNAUTHORIZED 명세 형식이며, 본문 requestId가 X-Request-Id 헤더와 같다")
    void unauthenticatedRequestReturnsSpecFormatted401() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/probe/ok"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
                .andReturn();

        String headerRequestId = result.getResponse().getHeader(RequestIdFilter.HEADER_NAME);
        assertThat(result.getResponse().getContentAsString()).contains("\"requestId\":\"" + headerRequestId + "\"");
    }

    @Test
    @DisplayName("보안 응답 헤더(nosniff, X-Frame-Options, CSP)가 적용된다")
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/api/v1/probe/ok").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
    }

    @Test
    @DisplayName("BusinessException은 명세의 에러 코드와 상태로 응답한다")
    void businessExceptionIsMappedToSpecCode() throws Exception {
        mockMvc.perform(get("/api/v1/probe/business").with(user("tester")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_CLOSED"));
    }

    @Test
    @DisplayName("검증 실패 시 400 INVALID_REQUEST이며 거부된 입력값(비밀번호 등)은 응답에 되돌려주지 않는다")
    void validationErrorDoesNotEchoRejectedValues() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/probe/validate").with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"password\":\"pw1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("pw1");
    }

    @Test
    @DisplayName("쿼리 파라미터 타입 불일치(?size=abc) 및 범위 검증 실패(?size=0)는 400 INVALID_REQUEST로 응답한다")
    void parameterValidationAndTypeMismatchReturn400() throws Exception {
        mockMvc.perform(get("/api/v1/probe/page?size=abc").with(user("tester")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("요청 파라미터 타입이 올바르지 않습니다: size"));

        mockMvc.perform(get("/api/v1/probe/page?size=0").with(user("tester")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    @DisplayName("잘못된 JSON 본문은 400 INVALID_REQUEST")
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/probe/validate").with(user("tester"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415 UNSUPPORTED_MEDIA_TYPE")
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/api/v1/probe/validate").with(user("tester"))
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("존재하지 않는 경로는 500이 아니라 404 NOT_FOUND")
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/does-not-exist").with(user("tester")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405 METHOD_NOT_ALLOWED")
    void wrongMethodReturns405() throws Exception {
        mockMvc.perform(delete("/api/v1/probe/business").with(user("tester")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("컨트롤러의 AccessDeniedException은 500이 아니라 403 FORBIDDEN")
    void accessDeniedInControllerReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/probe/forbidden").with(user("tester")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("예상하지 못한 예외는 500이며 내부 메시지를 노출하지 않는다")
    void unexpectedExceptionHidesInternalMessage() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/probe/boom").with(user("tester")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("db-password-leak");
    }

    @Test
    @DisplayName("CORS: 허용된 출처의 preflight(관리자 헤더 및 키오스크 헤더 포함)는 통과한다")
    void corsPreflightFromAllowedOriginSucceeds() throws Exception {
        mockMvc.perform(options("/api/v1/probe/validate")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type, Idempotency-Key, X-Device-Id, X-Device-Key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    @DisplayName("CORS: 허용되지 않은 출처의 preflight는 거부된다")
    void corsPreflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/probe/validate")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CORS: 허용 목록에 없는 비정상 헤더를 보내려 하면 preflight가 거부된다")
    void corsPreflightWithDisallowedHeaderIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/probe/validate")
                        .header("Origin", ALLOWED_ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-Evil-Custom-Header"))
                .andExpect(status().isForbidden());
    }

    @RestController
    @RequestMapping("/api/v1/probe")
    static class ProbeController {

        @GetMapping("/ok")
        Map<String, String> ok() {
            return Map.of("status", "ok");
        }

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ErrorCode.SESSION_CLOSED);
        }

        @GetMapping("/forbidden")
        void forbidden() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("db-password-leak");
        }

        @GetMapping("/page")
        Map<String, Integer> page(@RequestParam @Min(1) int size) {
            return Map.of("size", size);
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody ProbeRequest request) {
        }
    }

    record ProbeRequest(@NotBlank String name, @Size(min = 8) String password) {
    }
}
