package dev.alice.singularity.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface TaskAgent {

    @SystemMessage(fromResource = "prompts/system.md")
    String chat(@UserMessage String message);
}
