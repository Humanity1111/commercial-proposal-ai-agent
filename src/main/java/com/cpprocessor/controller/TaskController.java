package com.cpprocessor.controller;

import com.cpprocessor.dto.ApiError;
import com.cpprocessor.dto.TaskCreateResponse;
import com.cpprocessor.dto.TaskResponse;
import com.cpprocessor.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/cp")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Управление задачами обработки коммерческих предложений")
public class TaskController {

    private final TaskService taskService;

    @PostMapping(value = "/tasks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Создать задачу", description = "Создаёт новую задачу на асинхронную обработку загруженного архива с КП")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Задача создана и поставлена в очередь обработки"),
            @ApiResponse(responseCode = "400", description = "Невалидный файл",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Не авторизован",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TaskCreateResponse> createTask(
            @Parameter(description = "Файл с данными (zip, xlsx, pdf, docx, csv)")
            @RequestParam("file") MultipartFile file
    ) {
        TaskCreateResponse response = taskService.createTask(file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/tasks/{task_id}")
    @Operation(summary = "Получить задачу", description = "Возвращает текущее состояние задачи обработки и результат извлечения данных")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Задача найдена"),
            @ApiResponse(responseCode = "404", description = "Задача не найдена",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Не авторизован",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<TaskResponse> getTask(
            @Parameter(description = "ID задачи")
            @PathVariable("task_id") UUID taskId
    ) {
        TaskResponse response = taskService.getTask(taskId);
        return ResponseEntity.ok(response);
    }
}
