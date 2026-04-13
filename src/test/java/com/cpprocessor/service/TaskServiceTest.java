package com.cpprocessor.service;

import com.cpprocessor.dto.TaskCreateResponse;
import com.cpprocessor.entity.Task;
import com.cpprocessor.entity.TaskStatus;
import com.cpprocessor.exception.FileValidationException;
import com.cpprocessor.exception.TaskNotFoundException;
import com.cpprocessor.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskProcessingService taskProcessingService;

    @InjectMocks
    private TaskService taskService;

    @Test
    void createTask_Success() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.zip", "application/zip", "test content".getBytes());

        var savedTask = Task.builder()
                .id(UUID.randomUUID())
                .fileName("test.zip")
                .status(TaskStatus.PENDING)
                .build();

        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        TaskCreateResponse response = taskService.createTask(file);

        assertNotNull(response);
        assertEquals(savedTask.getId(), response.getId());
        assertEquals("PENDING", response.getStatus());

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertEquals("test.zip", taskCaptor.getValue().getFileName());
        assertEquals(TaskStatus.PENDING, taskCaptor.getValue().getStatus());
    }

    @Test
    void createTask_EmptyFile_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.zip", "application/zip", new byte[0]);

        assertThrows(FileValidationException.class, () -> taskService.createTask(file));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createTask_InvalidExtension_ThrowsException() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.exe", "application/octet-stream", "content".getBytes());

        assertThrows(FileValidationException.class, () -> taskService.createTask(file));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void createTask_NullFile_ThrowsException() {
        assertThrows(FileValidationException.class, () -> taskService.createTask(null));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void getTask_Found() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .fileName("test.xlsx")
                .status(TaskStatus.COMPLETED)
                .extractedData("{\"items\":[]}")
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        var response = taskService.getTask(taskId);

        assertNotNull(response);
        assertEquals(taskId, response.getId());
        assertEquals("test.xlsx", response.getFileName());
        assertEquals("COMPLETED", response.getStatus());
        assertEquals("{\"items\":[]}", response.getExtractedData());
    }

    @Test
    void getTask_NotFound_ThrowsException() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThrows(TaskNotFoundException.class, () -> taskService.getTask(taskId));
    }

    @Test
    void createTask_DelegatesAsyncProcessing() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "pdf content".getBytes());

        var savedTask = Task.builder()
                .id(UUID.randomUUID())
                .fileName("test.pdf")
                .status(TaskStatus.PENDING)
                .build();

        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);

        taskService.createTask(file);

        verify(taskProcessingService).processTaskAsync(eq(savedTask.getId()), any(byte[].class));
    }
}
