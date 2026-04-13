package com.cpprocessor.service;

import com.cpprocessor.dto.ExtractionResult;
import com.cpprocessor.entity.Task;
import com.cpprocessor.entity.TaskStatus;
import com.cpprocessor.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskProcessingServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private ExtractionService extractionService;

    @InjectMocks
    private TaskProcessingService taskProcessingService;

    @Test
    void processTaskAsync_Success() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .fileName("test.pdf")
                .status(TaskStatus.PENDING)
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));

        ExtractionResult mockResult = new ExtractionResult(
                List.of(new ExtractionResult.ExtractedItem("Test Product", "ART-001", 10.0, "шт", 100.0, "Supplier", null)),
                1);
        when(extractionService.extract(any(byte[].class), eq("test.pdf"))).thenReturn(mockResult);
        when(extractionService.toJson(mockResult)).thenReturn("{\"items\":[],\"total_positions\":1}");

        taskProcessingService.processTaskAsync(taskId, "test content".getBytes());

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository, atLeast(2)).save(taskCaptor.capture());

        Task lastSaved = taskCaptor.getAllValues().get(taskCaptor.getAllValues().size() - 1);
        assertEquals(TaskStatus.COMPLETED, lastSaved.getStatus());
        assertNotNull(lastSaved.getExtractedData());
        assertNull(lastSaved.getError());
        verify(extractionService).extract(any(byte[].class), eq("test.pdf"));
    }

    @Test
    void processTaskAsync_TaskNotFound_LogsError() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        taskProcessingService.processTaskAsync(taskId, "content".getBytes());

        verify(taskRepository, never()).save(any());
        verify(extractionService, never()).extract(any(), any());
    }

    @Test
    void processTaskAsync_ExtractionFails_SetsFailedStatus() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .fileName("broken.xlsx")
                .status(TaskStatus.PENDING)
                .build();

        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(i -> i.getArgument(0));
        when(extractionService.extract(any(byte[].class), eq("broken.xlsx")))
                .thenThrow(new RuntimeException("AI extraction failed"));

        taskProcessingService.processTaskAsync(taskId, "content".getBytes());

        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository, atLeast(2)).save(taskCaptor.capture());

        Task lastSaved = taskCaptor.getAllValues().get(taskCaptor.getAllValues().size() - 1);
        assertEquals(TaskStatus.FAILED, lastSaved.getStatus());
        assertEquals("AI extraction failed", lastSaved.getError());
    }
}
