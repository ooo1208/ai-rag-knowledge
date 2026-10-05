package io.github.ooo1208.domain.modelcatalog;

/**
 * 领域层的模型配置稳定标识。
 * 只表达“是哪一个配置”，不关心模型供应商、API Key 或具体客户端。
 */
public record ModelConfigId (String value){

    public ModelConfigId {
        if(value == null || value.isBlank()) {
            throw new IllegalArgumentException();
        }
        // 去掉首尾空格
        value = value.trim();
    }

}
