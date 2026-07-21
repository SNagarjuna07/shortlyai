package com.shortlyai.ai.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HeaderAuthFilterTests {

    private final HeaderAuthFilter filter = new HeaderAuthFilter();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_mcpPath_skipsAuth() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp/tools");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200); // untouched
    }

    @Test
    void doFilter_actuatorPath_skipsAuth() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_apiDocsPath_skipsAuth() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_missingUserIdHeader_returns401_chainNeverCalled() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ai/agent");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Missing X-User-Id header");
        verifyNoInteractions(chain);
    }

    @Test
    void doFilter_blankUserIdHeader_returns401() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ai/agent");
        request.addHeader("X-User-Id", "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(chain);
    }

    @Test
    void doFilter_validUserId_setsAuthenticationAndClearsAfter() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/ai/agent");
        request.addHeader("X-User-Id", "user-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] authNameDuringChain = new String[1];

        FilterChain chain = (req, res) ->
                authNameDuringChain[0] = SecurityContextHolder.getContext()
                        .getAuthentication()
                        .getName();

        filter.doFilterInternal(request, response, chain);

        assertThat(authNameDuringChain[0]).isEqualTo("user-123");
        // context must be cleared after the filter chain returns
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}