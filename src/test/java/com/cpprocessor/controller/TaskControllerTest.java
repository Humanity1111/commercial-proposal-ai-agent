package com.cpprocessor.controller;

import com.cpprocessor.config.SecurityConfig;
import com.cpprocessor.dto.TaskCreateResponse;
import com.cpprocessor.dto.TaskResponse;
import com.cpprocessor.entity.TaskStatus;
import com.cpprocessor.exception.TaskNotFoundException;
import com.cpprocessor.security.CustomUserDetailsService;
import com.cpprocessor.security.JwtService;
import com.cpprocessor.service.ExtractionService;
import com.cpprocessor.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@Import(SecurityConfig.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private ExtractionService extractionService;

    private String getValidToken() {
        String token = "valid-jwt-token";
        when(jwtService.isTokenValid(token)).thenReturn(true);
        when(jwtService.extractUsername(token)).thenReturn("admin");

        UserDetails userDetails = new User("admin", "password",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(customUserDetailsService.loadUserByUsername("admin")).thenReturn(userDetails);

        return token;
    }

    @Test
    void createTask_Authenticated_Returns202() throws Exception {
        String token = getValidToken();

        UUID taskId = UUID.randomUUID();
        TaskCreateResponse response = TaskCreateResponse.builder()
                .id(taskId)
                .status("PENDING")
                .build();
        when(taskService.createTask(any())).thenReturn(response);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.zip", "application/zip", "content".getBytes());

        mockMvc.perform(multipart("/cp/tasks")
                        .file(file)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id", is(taskId.toString())))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    void createTask_Unauthenticated_Returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.zip", "application/zip", "content".getBytes());

        mockMvc.perform(multipart("/cp/tasks")
                        .file(file)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTask_Authenticated_Returns200() throws Exception {
        String token = getValidToken();

        UUID taskId = UUID.randomUUID();
        TaskResponse response = TaskResponse.builder()
                .id(taskId)
                .fileName("test.xlsx")
                .status(TaskStatus.COMPLETED.name())
                .extractedData("{\"items\":[]}")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        when(taskService.getTask(taskId)).thenReturn(response);

        mockMvc.perform(get("/cp/tasks/{task_id}", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId.toString()))
                .andExpect(jsonPath("$.fileName").value("test.xlsx"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void getTask_NotFound_Returns404() throws Exception {
        String token = getValidToken();

        UUID taskId = UUID.randomUUID();
        when(taskService.getTask(taskId)).thenThrow(new TaskNotFoundException("Task not found: " + taskId));

        mockMvc.perform(get("/cp/tasks/{task_id}", taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }
}
