package com.cpprocessor.service;

import com.cpprocessor.dto.TaskCreateResponse;
import com.cpprocessor.dto.TaskResponse;
import com.cpprocessor.entity.Task;
import com.cpprocessor.entity.TaskStatus;
import com.cpprocessor.exception.FileValidationException;
import com.cpprocessor.exception.TaskNotFoundException;
import com.cpprocessor.repository.TaskRepository;
import com.cpprocessor.util.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".zip", ".rar", ".7z", ".xlsx", ".xls", ".pdf", ".docx", ".doc", ".csv"
    );

    private final TaskRepository taskRepository;
    private final TaskProcessingService taskProcessingService;

    public TaskCreateResponse createTask(MultipartFile file) {
        validateFile(file);

        var task = Task.builder()
                .fileName(file.getOriginalFilename())
                .status(TaskStatus.PENDING)
                .build();
        task = taskRepository.save(task);

        byte[] fileContent;
        try {
            fileContent = file.getBytes();
        } catch (Exception e) {
            throw new FileValidationException("Failed to read file content");
        }

        // вызов через отдельный бин — @Async работает через Spring AOP proxy
        taskProcessingService.processTaskAsync(task.getId(), fileContent);

        return TaskCreateResponse.builder()
                .id(task.getId())
                .status(task.getStatus().name())
                .message("Файл принят. Обработка AI-моделью может занять 1-3 минуты. Статус: GET /cp/tasks/" + task.getId())
                .build();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTask(UUID taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + taskId));
        return mapToResponse(task);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileValidationException("File is empty or not provided");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new FileValidationException("File name is missing");
        }

        // пока хардкод, можно вынести в конфиг
        String extension = FileUtils.getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileValidationException(
                    "Invalid file extension: " + extension + ". Allowed: " + ALLOWED_EXTENSIONS);
        }
    }

    private TaskResponse mapToResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .fileName(task.getFileName())
                .status(task.getStatus().name())
                .extractedData(task.getExtractedData())
                .error(task.getError())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
