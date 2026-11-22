package dev.alice.singularity.report;

import dev.alice.singularity.AliceSingularityApplication;
import dev.alice.singularity.agent.TaskAgent;
import dev.alice.singularity.cli.AgentCliRunner;
import dev.alice.singularity.singularity.SingularityClient;
import dev.alice.singularity.support.ContractAssert;
import dev.alice.singularity.support.EnvKeys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Живые проверки из REPORT.md §8.
 * Запускаются только при наличии GIGACHAT_AUTH_KEY и SINGULARITY_API_TOKEN (env или .env).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ReportScenariosIT {

    private static final Pattern TASK_ID = Pattern.compile("T-[0-9a-fA-F-]{36}");

    private static final AtomicReference<String> createdTaskId = new AtomicReference<>();
    private static final Set<String> createdTaskIds = new LinkedHashSet<>();
    private static ConfigurableApplicationContext context;
    private static TaskAgent taskAgent;
    private static SingularityClient singularityClient;

    @BeforeAll
    static void startApp() {
        EnvKeys.loadDotEnvIntoSystemProperties();
        assumeTrue(EnvKeys.hasLiveKeys(), "Skip REPORT live scenarios: keys not set");

        context = new SpringApplicationBuilder(AliceSingularityApplication.class)
                .profiles("test")
                .properties(
                        "app.cli.enabled=false",
                        "spring.main.web-application-type=none",
                        "spring.main.banner-mode=off"
                )
                .run();
        taskAgent = context.getBean(TaskAgent.class);
        singularityClient = context.getBean(SingularityClient.class);
    }

    @AfterAll
    static void stopApp() {
        try {
            deleteCreatedTasks();
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    private static void deleteCreatedTasks() {
        if (singularityClient == null || createdTaskIds.isEmpty()) {
            return;
        }
        String today = LocalDate.now().toString();
        for (String id : createdTaskIds) {
            try {
                singularityClient.updateTask(id, null, null, null, null, null, today);
                System.out.println("[ReportScenariosIT] deleted task " + id);
            } catch (Exception ex) {
                System.err.println("[ReportScenariosIT] failed to delete " + id + ": " + ex.getMessage());
            }
        }
    }

    private static void rememberTaskIds(String text) {
        if (text == null) {
            return;
        }
        Matcher m = TASK_ID.matcher(text);
        while (m.find()) {
            createdTaskIds.add(m.group());
        }
    }

    @Test
    @Order(1)
    void createTaskWithoutDate_reportCase1() {
        String answer = AgentCliRunner.sanitizeContract(taskAgent.chat("создай задачу купить молоко"));
        ContractAssert.assertSuccess(answer);
        ContractAssert.assertDataContains(answer, "T-");
        Matcher m = TASK_ID.matcher(answer);
        assertTrue(m.find(), "expected task id in answer:\n" + answer);
        createdTaskId.set(m.group());
        rememberTaskIds(answer);
    }

    @Test
    @Order(2)
    void createTaskForTomorrow_reportCase2() {
        String answer = AgentCliRunner.sanitizeContract(taskAgent.chat("поставь пробежка на завтра"));
        ContractAssert.assertSuccess(answer);
        ContractAssert.assertDataContains(answer, "пробежка");
        assertTrue(
                answer.contains("start") || answer.contains("2026-") || answer.contains("T-"),
                "expected dated task payload:\n" + answer
        );
        rememberTaskIds(answer);
    }

    @Test
    @Order(3)
    void listTasks_reportCase3() {
        String answer = AgentCliRunner.sanitizeContract(taskAgent.chat("какие у меня задачи"));
        ContractAssert.assertSuccess(answer);
        assertTrue(
                answer.contains("tasks") || answer.contains("count") || answer.contains("T-"),
                "expected list payload:\n" + answer
        );
    }

    @Test
    @Order(4)
    void updateTaskRename_reportCase4() {
        String id = createdTaskId.get();
        if (id == null || id.isBlank()) {
            String created = AgentCliRunner.sanitizeContract(taskAgent.chat("создай задачу тестовая для rename"));
            Matcher m = TASK_ID.matcher(created);
            assertTrue(m.find(), created);
            id = m.group();
            rememberTaskIds(created);
        }
        String answer = AgentCliRunner.sanitizeContract(
                taskAgent.chat("переименуй задачу " + id + " в Купить хлеб"));
        ContractAssert.assertSuccess(answer);
        ContractAssert.assertDataContains(answer, "Купить хлеб");
        ContractAssert.assertDataContains(answer, id);
        rememberTaskIds(answer);
    }

    @Test
    @Order(5)
    void offtopicRejected_reportCase5() {
        String answer = AgentCliRunner.sanitizeContract(taskAgent.chat("сколько будет 2+2"));
        ContractAssert.assertError(answer);
    }
}
