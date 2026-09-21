package io.github.ooo1208.api;

import io.github.ooo1208.api.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IRAGService {
    /**
     * 查询当前可用的知识库标签。
     *
     * @return 知识库标签列表
     */
    Response<List<String>> queryRagTagList();

    /**
     * 上传文件并将解析后的内容写入指定知识库。
     *
     * @param ragTag 知识库标签，用于区分文件所属的知识库
     * @param files  待上传的文件列表，不能为空
     * @return 文件上传结果
     */
    Response<String> uploadFile(String ragTag, List<MultipartFile> files);
}
