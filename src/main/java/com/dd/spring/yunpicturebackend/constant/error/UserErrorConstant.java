package com.dd.spring.yunpicturebackend.constant.error;

public interface UserErrorConstant {
    String PARAMS_EMPTY = "参数为空";
    String USER_ACCOUNT_SHORT = "用户账号过短";
    String USER_PASSWORD_SHORT = "用户密码过短";
    String ACCOUNT_DUP = "账号已重复";
    String USER_NOLOGIN = "用户未登录";

    String DIFFERENT_PASSWORDS = "两次输入的密码不一致";
    String LOGIN_DATABASE_ERROR = "登录失败,数据库错误";
    String REGISTER_DATABASE_ERROR = "注册失败,数据库错误";

    String DATA_ERROR = "用户不存在或密码错误";
}
