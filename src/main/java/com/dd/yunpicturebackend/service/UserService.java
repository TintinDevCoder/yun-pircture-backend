package com.dd.yunpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dd.yunpicturebackend.model.dto.user.UserLoginDTO;
import com.dd.yunpicturebackend.model.dto.user.UserQueryDTO;
import com.dd.yunpicturebackend.model.dto.user.UserRegisterDTO;
import com.dd.yunpicturebackend.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dd.yunpicturebackend.model.vo.user.UserLoginVO;
import com.dd.yunpicturebackend.model.vo.user.UserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

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
     * 登录用户信息脱敏
     * @param user
     * @return
     */
    UserLoginVO getLoginUserVO(User user);

    /**
     * 用户信息脱敏
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获取脱敏的用户列表视图对象
     * @param userList
     * @return
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 密码加密
     * @param password
     * @return
     */
    String getEncryptPassword(String password);

    public QueryWrapper<User> getQueryWrapper(UserQueryDTO userQueryDTO);

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

}
