package com.cpprocessor.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaWarmup {

    private final ChatModel chatModel;

    @EventListener(ApplicationReadyEvent.class)
    @Async("taskExecutor")
    public void warmup() {
        log.info("Прогрев AI-модели — загрузка в память GPU...");
        long start = System.currentTimeMillis();
        try {
            chatModel.call(new Prompt("Ответь одним словом: да",
                    OllamaOptions.builder()
                            .temperature(0.0)
                            .seed(42)
                            .build()));
            long elapsed = (System.currentTimeMillis() - start) / 1000;
            log.info("AI-модель прогрета и готова к работе ({} сек)", elapsed);
        } catch (Exception e) {
            log.warn("Прогрев AI-модели не удался (модель загрузится при первом запросе): {}", e.getMessage());
        }
    }
}
