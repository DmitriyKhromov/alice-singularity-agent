package dev.alice.singularity.cli;

import dev.alice.singularity.agent.TaskAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(0)
@ConditionalOnProperty(name = "app.cli.enabled", havingValue = "true")
public class AgentCliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AgentCliRunner.class);

    private final TaskAgent taskAgent;
    private final ConfigurableApplicationContext context;

    public AgentCliRunner(TaskAgent taskAgent, ConfigurableApplicationContext context) {
        this.taskAgent = taskAgent;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> phrases = args.getNonOptionArgs();
        if (phrases.isEmpty()) {
            System.err.println("""
                    Usage:
                      java -jar target/alice-singularity-agent-0.1.0.jar "создай задачу купить молоко на завтра"
                      mvn -q spring-boot:run -Dspring-boot.run.arguments="создай задачу купить молоко"
                    """);
            System.exit(SpringApplication.exit(context, () -> 1));
            return;
        }

        String userMessage = String.join(" ", phrases).trim();
        log.info("CLI user message: {}", userMessage);
        try {
            String answer = taskAgent.chat(userMessage);
            System.out.println(sanitizeContract(answer));
            System.exit(SpringApplication.exit(context, () -> 0));
        } catch (Exception ex) {
            log.error("Agent failed", ex);
            System.out.println("""
                    Status: error
                    Action: agent
                    Data: null
                    Errors: %s
                    """.formatted(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()).trim());
            System.exit(SpringApplication.exit(context, () -> 2));
        }
    }

    /** GigaChat иногда подставляет плейсхолдеры вместо кавычек в JSON. */
    public static String sanitizeContract(String answer) {
        if (answer == null) {
            return "";
        }
        return answer
                .replace("<|superquote|>", "\"")
                .replace("<|quote|>", "\"")
                .trim();
    }
}
