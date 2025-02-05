package com.dd.yunpicturebackend.aop;

import com.dd.yunpicturebackend.annotation.AuthCheck;
import com.dd.yunpicturebackend.enums.UserRoleEnum;
import com.dd.yunpicturebackend.exception.BusinessException;
import com.dd.yunpicturebackend.exception.ErrorCode;
import com.dd.yunpicturebackend.exception.ThrowUtils;
import com.dd.yunpicturebackend.model.entity.User;
import com.dd.yunpicturebackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 获取当前方法权限
        String mustRole = authCheck.mustRole();
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 获取当前登录用户
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        User loginUser = userService.getLoginUser(request);
        //若不需要权限 放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 必须有权限 才会通过
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        ThrowUtils.throwIf(userRoleEnum == null, new BusinessException(ErrorCode.NO_AUTH_ERROR));
        // 要求必须有管理员权限，但用户没有管理员权限，拒绝
        ThrowUtils.throwIf(UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum), new BusinessException(ErrorCode.NO_AUTH_ERROR));
        // 通过权限校验，放行
        return joinPoint.proceed();
    }
}
