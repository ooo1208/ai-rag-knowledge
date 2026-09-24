package io.github.ooo1208.http;

import io.github.ooo1208.api.IRAGService;
import io.github.ooo1208.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ai.document.Document;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
public class RAGController implements IRAGService {

    @Resource
    private OllamaChatModel ollamaChatModel;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;
    @Resource
    private RedissonClient redissonClient;

    @GetMapping("query_rag_tag_list")
    @Override
    public Response<List<String>> queryRagTagList() {
        RList<String> elements = redissonClient.getList("ragTag");
        return Response.<List<String>>builder()
                .code("0000")
                .info("调用成功")
                .data(elements)
                .build();
    }

    @RequestMapping(
            value = "file/upload",
            method = RequestMethod.POST,
            headers = "content-type=multipart/form-data"
    )
    @Override
    public Response<String> uploadFile(String ragTag, List<MultipartFile> files) {
        log.info("上传知识库开始 {}",ragTag);
        for(MultipartFile file : files) {
            TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());
            List<Document> documents = documentReader.get();
            List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

            documents.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));
            documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge",ragTag));

            pgVectorStore.accept(documentSplitterList);

            RList<String> elements = redissonClient.getList("ragTag");
            if(!elements.contains(ragTag)){
                elements.add(ragTag);
            }
        }

        log.info("上传知识库完成 {}", ragTag);
        return Response.<String>builder().code("0000").info("调用成功").data(ragTag).build();
    }

    /**
     * 分析Git仓库，将所有文件中的文本内容提取出来，上传到知识库中
     * @param repoUrl  Git仓库URL
     * @param username Git仓库用户名
     * @param token    Git仓库密码
     * @return 分析结果
     */
    @PostMapping("analyze_git_repository")
    public Response<String> analyzeGitRepository(@RequestParam String repoUrl, @RequestParam String username, @RequestParam String token) throws Exception {
        String localPath = "./git-cloned-repo";
        String repoProjectName = extractRepoProjectName(repoUrl);
        log.info("克隆路径：{}", new File(localPath).getAbsolutePath());

        FileUtils.deleteDirectory(new File(localPath));

        Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
                .call();
        git.close();

        Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.info("遍历解析路径，上传知识库：项目={}, 文件={}", repoProjectName, file.getFileName().toString());
                try {
                    TikaDocumentReader documentReader = new TikaDocumentReader(new PathResource(file));
                    List<Document> documents = documentReader.get();
                    List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

                    documents.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));
                    documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));
                    pgVectorStore.accept(documentSplitterList);
                } catch (Exception e) {
                    log.error("遍历解析路径，上传知识库：项目={}, 文件={} 失败", repoProjectName, file.getFileName().toString(), e);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        FileUtils.deleteDirectory(new File(localPath));

        RList<String> elements = redissonClient.getList("ragTag");
        if(!elements.contains(repoProjectName)){
            elements.add(repoProjectName);
        }

        git.close();

        log.info("遍历解析路径，上传知识库完成项目：{}", repoUrl);

        return Response.<String>builder().code("0000").info("调用成功").build();
    }

    private String extractRepoProjectName(String repoUrl) {
        String[] parts = repoUrl.split("/");
        String projectNameWithGit = parts[parts.length - 1];
        return projectNameWithGit.replace(".git", "");
    }
}
