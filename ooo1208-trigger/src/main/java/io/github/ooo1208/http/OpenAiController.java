package io.github.ooo1208.http;

import io.github.ooo1208.api.IAiService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.observation.AbstractObservationVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController()
@RequestMapping("/api/v1/openai/")
@CrossOrigin("*")
public class OpenAiController implements IAiService {

    @Resource
    private OpenAiChatModel chatModel;
    @Autowired
    private AbstractObservationVectorStore pgVectorStore;

    @GetMapping("/generate")
    @Override
    public ChatResponse generate(String model, String message) {
        return chatModel.call(new Prompt(
                message,
                OpenAiChatOptions.builder()
                        .model(model)
                        .build()
        ));
    }

    @GetMapping("/generate_stream")
    @Override
    public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam String message) {
        return chatModel.stream(new Prompt(
                message,
                OpenAiChatOptions.builder()
                        .model(model)
                        .build()
        ));
    }

    @GetMapping("/generate_stream_rag")
    @Override
    public Flux<ChatResponse> generateStreamRag(String model, String ragTag, String message) {

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        SearchRequest request = SearchRequest.builder()
                .query(message)
                .topK(5)
                .filterExpression("knowledge == '" + ragTag + "'")
                .build();

        List<Document> documents = pgVectorStore.similaritySearch(request);
        List<String> documentContents = documents.stream().map(Document::getText).toList();

        PromptTemplate promptTemplate = new PromptTemplate(SYSTEM_PROMPT);
        String renderedPrompt = promptTemplate.render(Map.of("documents", documentContents));
        SystemMessage systemMessage = new SystemMessage(renderedPrompt);
        Prompt prompt = new Prompt(List.of(systemMessage, new UserMessage(message)));
        return chatModel.stream(prompt);
    }
}
