package dev.alice.singularity.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class LiveKeysCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (EnvKeys.hasLiveKeys()) {
            return ConditionEvaluationResult.enabled("GIGACHAT_AUTH_KEY and SINGULARITY_API_TOKEN are set");
        }
        return ConditionEvaluationResult.disabled("Skip live REPORT scenarios: keys not set in env/.env");
    }
}
