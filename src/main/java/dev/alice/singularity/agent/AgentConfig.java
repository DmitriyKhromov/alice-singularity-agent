package dev.alice.singularity.agent;

import chat.giga.client.auth.AuthClient;
import chat.giga.client.auth.AuthClientBuilder;
import chat.giga.langchain4j.GigaChatChatModel;
import chat.giga.langchain4j.GigaChatChatRequestParameters;
import chat.giga.model.Scope;
import dev.alice.singularity.config.AppProperties;
import dev.alice.singularity.tools.CreateTaskTool;
import dev.alice.singularity.tools.ListTasksTool;
import dev.alice.singularity.tools.UpdateTaskTool;
import dev.langchain4j.model.moderation.DisabledModerationModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    GigaChatChatModel gigaChatChatModel(AppProperties properties) {
        AppProperties.GigaChat gigachat = properties.gigachat();
        if (gigachat.authKey() == null || gigachat.authKey().isBlank()) {
            throw new IllegalStateException("Не задан GIGACHAT_AUTH_KEY");
        }
        Scope scope = parseScope(gigachat.scope());
        return GigaChatChatModel.builder()
                .maxRetries(3)
                .defaultChatRequestParameters(GigaChatChatRequestParameters.builder()
                        .modelName(gigachat.model())
                        .profanityCheck(false)
                        .build())
                .authClient(AuthClient.builder()
                        .withOAuth(AuthClientBuilder.OAuthBuilder.builder()
                                .scope(scope)
                                .authKey(gigachat.authKey().trim())
                                .build())
                        .build())
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    TaskAgent taskAgent(
            GigaChatChatModel model,
            CreateTaskTool createTaskTool,
            ListTasksTool listTasksTool,
            UpdateTaskTool updateTaskTool
    ) {
        return AiServices.builder(TaskAgent.class)
                .chatModel(model)
                .moderationModel(new DisabledModerationModel())
                .tools(createTaskTool, listTasksTool, updateTaskTool)
                .maxSequentialToolsInvocations(8)
                .build();
    }

    private static Scope parseScope(String raw) {
        if (raw == null || raw.isBlank()) {
            return Scope.GIGACHAT_API_PERS;
        }
        return Scope.valueOf(raw.trim());
    }
}
