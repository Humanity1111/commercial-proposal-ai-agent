package com.cpprocessor.service;

import com.cpprocessor.dto.ExtractionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExtractionService {

    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
            ".zip", ".rar", ".7z"
    );

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final FileParserService fileParserService;

    public ExtractionResult extract(byte[] fileContent, String fileName) {
        log.info("Starting extraction for file: {}", fileName);

        try {
            if (fileParserService.isStructuredProcessingTasksExcel(fileContent, fileName)) {
                log.info("Detected structured processing-tasks Excel format: {}", fileName);
                return extractStructuredExcel(fileContent);
            }

            return extractWithLlm(fileContent, fileName);
        } catch (Exception e) {
            log.error("Extraction failed for {}", fileName, e);
            throw new RuntimeException("Extraction failed for file: " + fileName, e);
        }
    }

    private ExtractionResult extractWithLlm(byte[] fileContent, String fileName) {
        var outputConverter = new BeanOutputConverter<>(ExtractionResult.class);
        String prompt = buildPrompt(fileContent, fileName);

        ChatResponse response = chatModel.call(
                new Prompt(
                        prompt,
                        OllamaOptions.builder()
                                .temperature(0.0)
                                .seed(42)
                                .format(outputConverter.getJsonSchemaMap())
                                .build()
                )
        );

        String content = response.getResult().getOutput().getText();
        log.info("Raw AI response for {}: {}", fileName, content);

        if (content == null || content.isBlank()) {
            throw new IllegalStateException("LLM returned empty response");
        }

        content = cleanupJson(content);

        return outputConverter.convert(content);
    }

    private ExtractionResult extractStructuredExcel(byte[] fileContent) throws Exception {
        List<Map<String, String>> rows = fileParserService.parseStructuredProcessingTasksExcel(fileContent);

        List<ExtractionResult.ExtractedItem> items = rows.stream()
                .map(this::mapStructuredTaskRowToItem)
                .toList();

        return new ExtractionResult(items, items.size());
    }

    private ExtractionResult.ExtractedItem mapStructuredTaskRowToItem(Map<String, String> row) {
        String id = normalize(row.get("id"));
        String fileName = normalize(row.get("file_name"));
        String status = normalize(row.get("status"));
        String extractedData = normalize(row.get("extracted_data"));
        String error = normalize(row.get("error"));
        String createdAt = normalize(row.get("created_at"));
        String updatedAt = normalize(row.get("updated_at"));

        String supplier = extractSupplierName(extractedData);
        String notes = buildNotes(status, error, createdAt, updatedAt, extractedData);

        return new ExtractionResult.ExtractedItem(
                fileName,
                id,
                1.0,
                "file",
                null,
                supplier,
                notes
        );
    }

    private String extractSupplierName(String extractedData) {
        if (extractedData == null || extractedData.isBlank() || extractedData.equalsIgnoreCase("nan")) {
            return null;
        }

        try {
            String normalizedJson = extractedData
                    .replace("\r", " ")
                    .replace("\n", " ")
                    .trim();

            JsonNode root = objectMapper.readTree(normalizedJson);
            JsonNode suppliers = root.path("suppliers");

            if (suppliers.isArray() && !suppliers.isEmpty()) {
                JsonNode firstSupplier = suppliers.get(0);
                String supplierName = firstSupplier.path("name").asText(null);
                if (supplierName != null && !supplierName.isBlank()) {
                    return supplierName;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse extracted_data JSON for supplier extraction: {}", e.getMessage());
        }

        return null;
    }

    private String buildNotes(String status, String error, String createdAt, String updatedAt, String extractedData) {
        List<String> notes = new ArrayList<>();

        if (status != null) {
            notes.add("status: " + status);
        }
        if (error != null && !error.equalsIgnoreCase("nan")) {
            notes.add("error: " + error);
        }
        if (createdAt != null) {
            notes.add("created_at: " + createdAt);
        }
        if (updatedAt != null) {
            notes.add("updated_at: " + updatedAt);
        }
        if (extractedData != null && !extractedData.equalsIgnoreCase("nan")) {
            notes.add("extracted_data: " + extractedData);
        }

        return String.join("; ", notes);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value
                .replace("\r", " ")
                .replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (trimmed.isBlank() || trimmed.equalsIgnoreCase("nan")) {
            return null;
        }

        return trimmed;
    }

    private String cleanupJson(String content) {
        String cleaned = content.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3).trim();
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        return cleaned;
    }

    private String buildPrompt(byte[] fileContent, String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();

        if (ARCHIVE_EXTENSIONS.contains(extension) && !extension.equals(".zip")) {
            return """
                    Файл "%s" является архивом формата %s. Содержимое недоступно для анализа.

                    Верни строго такой JSON:
                    {
                      "items": [],
                      "total_positions": 0
                    }
                    """.formatted(fileName, extension);
        }

        String textContent = fileParserService.parse(fileContent, fileName);

        if (textContent.length() > 15000) {
            textContent = textContent.substring(0, 15000) + "\n... (содержимое обрезано)";
        }

        return """
                Ты извлекаешь товарные позиции из коммерческих предложений, прайс-листов, спецификаций и закупочных документов.

                ЗАДАЧА:
                Проанализируй содержимое документа и извлеки только реальные товарные позиции.

                ВАЖНО:
                - Верни ТОЛЬКО валидный JSON
                - Не используй markdown
                - Не добавляй пояснения
                - Не добавляй текст до или после JSON
                - Не копируй технические куски документа как есть, если они не являются товарной строкой
                - Не вставляй JSON или псевдо-JSON из текста документа внутрь полей результата
                - Игнорируй служебные блоки, заголовки, подписи, реквизиты, инструкции, номера страниц, общие комментарии, если они не являются товарной позицией

                JSON должен быть СТРОГО такого вида:
                {
                  "items": [
                    {
                      "product_name": "string",
                      "article": "string or null",
                      "quantity": number or null,
                      "unit": "string or null",
                      "price": number or null,
                      "supplier": "string or null",
                      "notes": "string or null"
                    }
                  ],
                  "total_positions": number
                }

                ПРАВИЛА:
                1. Один товар = один объект в массиве items.
                2. Не дублируй одинаковые или почти одинаковые позиции.
                3. Не включай заголовки таблиц в items.
                4. Если поле неизвестно — ставь null.
                5. quantity должна быть только числом.
                6. price должна быть только числом и означать цену за 1 единицу.
                7. unit должна содержать только единицу измерения, например: "шт", "кг", "мешок", "мешков", "л", "м", "упак".
                8. article должен содержать только артикул / код / SKU, если он явно указан.
                9. supplier должен содержать только название поставщика, если оно явно есть в документе.
                10. notes должны содержать только дополнительную полезную информацию, не поместившуюся в основные поля.
                11. Не смешивай несколько полей в одно.
                12. Не вставляй в unit, product_name, article, supplier или notes куски JSON.
                13. Если указана только общая сумма по строке, а цена за единицу отсутствует, то price = null.
                14. total_positions должен быть равен количеству объектов в items.
                15. Если документ содержит таблицу, извлекай каждую строку таблицы как отдельную товарную позицию.
                16. Если в документе встречаются реквизиты, адреса, телефоны, даты, подписи и служебный текст — не считай их товарами.
                17. Если в документе встречается уже готовый JSON, не копируй его как есть в ответ. Извлеки из него только нужные поля в требуемую схему.
                18. Если товаров нет, верни:
                {
                  "items": [],
                  "total_positions": 0
                }

                ПРИМЕР КОРРЕКТНОГО ОТВЕТА:
                {
                  "items": [
                    {
                      "product_name": "Цемент М500",
                      "article": null,
                      "quantity": 200,
                      "unit": "мешков",
                      "price": 350,
                      "supplier": "ООО СтройСнаб",
                      "notes": null
                    },
                    {
                      "product_name": "Арматура А500С 12мм",
                      "article": null,
                      "quantity": 5000,
                      "unit": "кг",
                      "price": 78,
                      "supplier": "ООО СтройСнаб",
                      "notes": null
                    }
                  ],
                  "total_positions": 2
                }

                Файл: %s

                Содержимое документа:
                %s
                """.formatted(fileName, textContent);
    }

    public String toJson(ExtractionResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Failed to serialize ExtractionResult", e);
            throw new RuntimeException("Failed to serialize extraction result", e);
        }
    }
}
