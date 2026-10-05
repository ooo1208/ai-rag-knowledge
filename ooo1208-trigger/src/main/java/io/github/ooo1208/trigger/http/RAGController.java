package io.github.ooo1208.trigger.http;

import io.github.ooo1208.application.rag.command.AnalyzeGitRepositoryCommand;
import io.github.ooo1208.application.rag.command.UploadRagFilesCommand;
import io.github.ooo1208.application.rag.model.RagFile;
import io.github.ooo1208.application.rag.port.in.AnalyzeGitRepositoryUseCase;
import io.github.ooo1208.application.rag.port.in.QueryRagTagsUseCase;
import io.github.ooo1208.application.rag.port.in.UploadRagFilesUseCase;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 知识库 HTTP 入口适配器。
 * 文件、Git 参数会在这里转换成 application 命令，具体解析和存储交给出站适配器。
 */
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/rag")
public class RagController {

    private final QueryRagTagsUseCase queryRagTagsUseCase;
    private final UploadRagFilesUseCase uploadRagFilesUseCase;
    private final AnalyzeGitRepositoryUseCase analyzeGitRepositoryUseCase;

    public RagController(
            QueryRagTagsUseCase queryRagTagsUseCase,
            UploadRagFilesUseCase uploadRagFilesUseCase,
            AnalyzeGitRepositoryUseCase analyzeGitRepositoryUseCase
    ) {
        this.queryRagTagsUseCase = Objects.requireNonNull(queryRagTagsUseCase);
        this.uploadRagFilesUseCase = Objects.requireNonNull(uploadRagFilesUseCase);
        this.analyzeGitRepositoryUseCase = Objects.requireNonNull(
                analyzeGitRepositoryUseCase
        );
    }

    @GetMapping("/query_rag_tag_list")
    public ApiResponse<List<String>> queryRagTagList() {
        return ApiResponse.<List<String>>builder()
                .code("0000")
                .info("调用成功")
                .data(queryRagTagsUseCase.query())
                .build();
    }

    @PostMapping("/file/upload")
    public ApiResponse<String> uploadFile(
            @RequestParam String ragTag,
            @RequestPart("files") List<MultipartFile> files
    ) throws IOException {
        List<RagFile> ragFiles = files.stream()
                .map(this::toRagFile)
                .toList();

        uploadRagFilesUseCase.upload(
                new UploadRagFilesCommand(ragTag, ragFiles)
        );

        return ApiResponse.<String>builder()
                .code("0000")
                .info("调用成功")
                .data(ragTag)
                .build();
    }

    @PostMapping("/analyze_git_repository")
    public ApiResponse<String> analyzeGitRepository(
            @RequestParam String repoUrl,
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String token
    ) {
        analyzeGitRepositoryUseCase.analyze(
                new AnalyzeGitRepositoryCommand(repoUrl, username, token)
        );

        return ApiResponse.<String>builder()
                .code("0000")
                .info("调用成功")
                .build();
    }

    private RagFile toRagFile(MultipartFile file) {
        try {
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isBlank()) {
                filename = "uploaded-file";
            }
            return new RagFile(filename, file.getBytes());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to read uploaded file: " + file.getOriginalFilename(),
                    exception
            );
        }
    }
}
