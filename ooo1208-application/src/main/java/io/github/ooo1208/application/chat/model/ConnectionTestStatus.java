package io.github.ooo1208.application.chat.model;

/**
 * 模型连接测试的可操作结果分类。
 */
public enum ConnectionTestStatus {

    SUCCESS,

    AUTHENTICATION_FAILED,

    NETWORK_ERROR,

    PROVIDER_UNAVAILABLE,

    MODEL_NOT_FOUND,

    UNSUPPORTED
}
