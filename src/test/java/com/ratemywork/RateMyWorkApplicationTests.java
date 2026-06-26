package com.ratemywork;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratemywork.dto.Requests;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateMyWorkApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void publicEndpointsAreOpen() throws Exception {
        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/stats/public"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/companies/top"))
                .andExpect(status().isOk());
    }

    @Test
    void seedDataExposesApprovedCompanies() throws Exception {
        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").exists());
    }

    @Test
    void adminApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationCreatesAccount() throws Exception {
        var request = new Requests.RegisterRequest(
                "Test User", "test_user_1", "test1@example.com", "+15551230000", "Password123");
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("test_user_1"))
                .andExpect(jsonPath("$.fullyVerified").value(false));
    }

    @Test
    void registrationRejectsBadUsername() throws Exception {
        var request = new Requests.RegisterRequest(
                "Bad", "x", "bad@example.com", "+15551230000", "Password123");
        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submittingFeedbackRequiresLogin() throws Exception {
        var request = new Requests.FeedbackRequest(1L, 5, "Nice", "Great place");
        mockMvc.perform(post("/api/feedback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
