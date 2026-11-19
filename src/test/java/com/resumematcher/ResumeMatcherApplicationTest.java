package com.resumematcher;

import static org.assertj.core.api.Assertions.assertThat;

import com.resumematcher.llm.AnthropicLlmClient;
import com.resumematcher.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "anthropic.api-key=")
class ResumeMatcherApplicationTest {

    @Autowired
    private LlmClient llmClient;

    @Test
    void elContextoArrancaConElClienteDeAnthropic() {
        assertThat(llmClient).isInstanceOf(AnthropicLlmClient.class);
    }
}
