package com.dd.spring.yunpicturebackend.controller;

import com.dd.spring.yunpicturebackend.annotation.AuthCheck;
import com.dd.spring.yunpicturebackend.common.BaseResponse;
import com.dd.spring.yunpicturebackend.common.ResultUtils;
import com.dd.spring.yunpicturebackend.exception.ErrorCode;
import com.dd.spring.yunpicturebackend.exception.ThrowUtils;
import com.dd.spring.yunpicturebackend.model.dto.UserLoginDTO;
import com.dd.spring.yunpicturebackend.model.dto.UserRegisterDTO;
import com.dd.spring.yunpicturebackend.model.entity.User;
import com.dd.spring.yunpicturebackend.model.vo.UserLoginVO;
import com.dd.spring.yunpicturebackend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;

    /**
     * 用户登录
     * @param userLoginDTO
     * @param request
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<UserLoginVO> userLogin(@RequestBody UserLoginDTO userLoginDTO, HttpServletRequest request) {
        //判空
        ThrowUtils.throwIf(userLoginDTO == null, ErrorCode.PARAMS_ERROR);
        //登录
        UserLoginVO userLoginVO = userService.userLogin(userLoginDTO, request);
        return ResultUtils.success(userLoginVO);
    }

    /**
     * 用户注册
     * @param userRegisterDTO
     * @return
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterDTO userRegisterDTO) {
        //判空
        ThrowUtils.throwIf(userRegisterDTO == null, ErrorCode.PARAMS_ERROR);
        //注册
        long result = userService.userRegister(userRegisterDTO);
        return ResultUtils.success(result);
    }

    /**
     * 获取当前登录用户
     * @param request
     * @return
     */
    @GetMapping("/get/login")
    public BaseResponse<UserLoginVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    /**
     * 用户注销
     * @param request
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        boolean result = userService.LogoutUser(request);
        return ResultUtils.success(result);
    }
}
