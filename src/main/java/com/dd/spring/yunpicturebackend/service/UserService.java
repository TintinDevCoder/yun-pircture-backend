package com.dd.spring.yunpicturebackend.service;

import com.dd.spring.yunpicturebackend.model.dto.UserLoginDTO;
import com.dd.spring.yunpicturebackend.model.dto.UserRegisterDTO;
import com.dd.spring.yunpicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dd.spring.yunpicturebackend.model.vo.UserLoginVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author DELL
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2025-01-25 10:28:26
*/
public interface UserService extends IService<User> {
    /**
     * 用户注册
     * @param userRegisterDTO
     * @return
     */
    long userRegister(UserRegisterDTO userRegisterDTO);

    /**
     * 用户登录
     * @param userLoginDTO
     * @param request
     * @return
     */
    UserLoginVO userLogin(UserLoginDTO userLoginDTO, HttpServletRequest request);

    /**
     * 获取当前用户
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 用户注销
     * @param request
     * @return
     */
    boolean LogoutUser(HttpServletRequest request);
    /**
     * 用户信息脱敏
     * @param user
     * @return
     */
    UserLoginVO getLoginUserVO(User user);

    /**
     * 密码加密
     * @param password
     * @return
     */
    String getEncryptPassword(String password);


}
