package io.github.ooo1208.http;

import io.github.ooo1208.api.IAiService;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/ollama/")
public class OllamaController implements IAiService {

    @Autowired
    private OllamaChatModel chatModel;

    /**
     * http://localhost:8090/api/v1/ollama/generate?model=deepseek-r1:1.5b&message=1+1
     */
    @GetMapping("/generate")
    @Override
    public ChatResponse generate(@RequestParam String model, @RequestParam String message) {
        return chatModel.call(new Prompt(
                message,
                OllamaOptions.builder().model(model).build())
        );
    }

    /**
     * http://localhost:8090/api/v1/ollama/generate_stream?model=deepseek-r1:1.5b&message=hi
     */
    @GetMapping("/generate_stream")
    @Override
    public Flux<ChatResponse> generateStream(String model, String message) {
        return chatModel.stream(new Prompt(message, OllamaOptions.builder().model(model).build()));
    }


}
