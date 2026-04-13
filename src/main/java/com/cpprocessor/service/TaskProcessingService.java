package com.cpprocessor.service;

import com.cpprocessor.dto.ExtractionResult;
import com.cpprocessor.entity.Task;
import com.cpprocessor.entity.TaskStatus;
import com.cpprocessor.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskProcessingService {

    private final TaskRepository taskRepository;
    private final ExtractionService extractionService;

    @Async("taskExecutor")
    public void processTaskAsync(UUID taskId, byte[] fileContent) {
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            log.error("Task not found for async processing: {}", taskId);
            return;
        }

        log.info("[Task {}] Начало обработки файла: {}", taskId, task.getFileName());
        task.setStatus(TaskStatus.PROCESSING);
        taskRepository.save(task);

        try {
            log.info("[Task {}] Отправка запроса в AI-модель (это может занять 1-3 минуты)...", taskId);
            long start = System.currentTimeMillis();

            ExtractionResult result = extractionService.extract(fileContent, task.getFileName());

            long elapsed = (System.currentTimeMillis() - start) / 1000;
            log.info("[Task {}] AI-модель ответила за {} сек, извлечено {} позиций", taskId, elapsed, result.totalPositions());

            String extractedData = extractionService.toJson(result);

            task.setExtractedData(extractedData);
            task.setStatus(TaskStatus.COMPLETED);
            taskRepository.save(task);

            log.info("[Task {}] Задача завершена успешно", taskId);
        } catch (Exception e) {
            task.setStatus(TaskStatus.FAILED);
            task.setError(e.getMessage());
            taskRepository.save(task);

            log.error("[Task {}] Ошибка: {}", taskId, e.getMessage());
        }
    }
}
