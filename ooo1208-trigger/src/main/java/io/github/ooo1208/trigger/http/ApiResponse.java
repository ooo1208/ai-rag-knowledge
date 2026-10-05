package io.github.ooo1208.trigger.http;

import java.io.Serializable;

/**
 * HTTP 层统一返回包装。
 * 它属于 trigger 层，不进入 application，避免业务层依赖接口协议。
 */
public class ApiResponse<T> implements Serializable {

    private String code;
    private String info;
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(String code, String info, T data) {
        this.code = code;
        this.info = info;
        this.data = data;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getInfo() {
        return info;
    }

    public void setInfo(String info) {
        this.info = info;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static final class Builder<T> {

        private String code;
        private String info;
        private T data;

        public Builder<T> code(String code) {
            this.code = code;
            return this;
        }

        public Builder<T> info(String info) {
            this.info = info;
            return this;
        }

        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

        public ApiResponse<T> build() {
            return new ApiResponse<>(code, info, data);
        }
    }
}
