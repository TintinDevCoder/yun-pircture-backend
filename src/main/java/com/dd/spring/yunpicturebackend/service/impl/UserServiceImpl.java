package com.dd.spring.yunpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dd.spring.yunpicturebackend.constant.UserConstant;
import com.dd.spring.yunpicturebackend.enums.UserRoleEnum;
import com.dd.spring.yunpicturebackend.exception.BusinessException;
import com.dd.spring.yunpicturebackend.exception.ErrorCode;
import com.dd.spring.yunpicturebackend.exception.ThrowUtils;
import com.dd.spring.yunpicturebackend.model.dto.user.UserLoginDTO;
import com.dd.spring.yunpicturebackend.model.dto.user.UserQueryDTO;
import com.dd.spring.yunpicturebackend.model.dto.user.UserRegisterDTO;
import com.dd.spring.yunpicturebackend.model.entity.User;
import com.dd.spring.yunpicturebackend.model.vo.user.UserLoginVO;
import com.dd.spring.yunpicturebackend.model.vo.user.UserVO;
import com.dd.spring.yunpicturebackend.service.UserService;
import com.dd.spring.yunpicturebackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
* @author DELL
* @description 针对表【user(用户)】的数据库操作Service实现
* @createDate 2025-01-25 10:28:26
*/
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService{

    @Override
    public long userRegister(UserRegisterDTO userRegisterDTO) {
        // 获取用户注册信息
        String userAccount = userRegisterDTO.getUserAccount();
        String userPassword = userRegisterDTO.getUserPassword();
        String checkPassword = userRegisterDTO.getCheckPassword();

        // 1. 校验参数
        // 检查是否有参数为空
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        // 检查用户账号长度是否符合要求
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        // 检查用户密码长度是否符合要求
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 检查两次输入的密码是否一致
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        //2、检查用户账号是否和数据库中已有的重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        Long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号已重复");
        }
        //密码加密
        String encryptPassword = getEncryptPassword(userPassword);
        //插入数据到数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(UserRoleEnum.USER.getValue());
        try {
            // 尝试保存用户信息到数据库
            boolean saveResult = this.save(user);
            if (!saveResult) {
                // 如果保存失败，抛出异常
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败,数据库错误");
            }
            // 返回用户ID
            return user.getId();
        } catch (Exception e) {
            // 捕获异常，并抛出自定义异常
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败,数据库错误" + e.getMessage());
        }
    }

    /**
     * 用户登录方法
     *
     * @param userLoginDTO 用户登录信息，包含用户账号和密码
     * @param request HTTP请求对象，用于获取会话信息
     * @return 返回用户登录成功后的信息对象
     * @throws BusinessException 当参数验证失败、用户不存在或数据库查询出错时抛出业务异常
     */
    @Override
    public UserLoginVO userLogin(UserLoginDTO userLoginDTO, HttpServletRequest request) {
        // 获取用户输入的账号和密码
        String userAccount = userLoginDTO.getUserAccount();
        String userPassword = userLoginDTO.getUserPassword();

        //1、校验输入参数的合法性
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }

        //2、对用户传递的密码进行加密处理
        String encryptPassword = getEncryptPassword(userPassword);

        //3、根据账号和加密后的密码查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper
                .eq("userAccount", userAccount)
                .eq("userPassword", encryptPassword);
        User user = null;
        try{
            user = this.baseMapper.selectOne(queryWrapper);
        }catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录失败,数据库错误" + e.getMessage());
        }

        // 如果查询结果为空，说明用户不存在或密码错误
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }

        //4、保存用户登录态
        // 获取用户的session
        HttpSession session = request.getSession();
        // 将用户信息保存到session中，以维持登录状态
        session.setAttribute(UserConstant.USER_LOGIN_STATE, user);

        // 返回登录成功的用户信息视图对象
        return getLoginUserVO(user);
    }

    /**
     * 获取当前登录的用户信息
     *
     * @param request HTTP请求对象，用于获取当前会话中的用户登录状态
     * @return 如果用户已登录且信息存在，则返回User对象；否则返回null
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        User currentUser = (User)request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        // 如果会话中没有用户登录状态或用户ID为空，则抛出未登录异常
        ThrowUtils.throwIf(currentUser == null || currentUser.getId() == null, new BusinessException(ErrorCode.NOT_LOGIN_ERROR));

        // 从数据库查询（追求性能的话可以注释，直接返回上述结果）
        // 根据当前用户的ID从数据库中查询最新的用户信息
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        // 如果从数据库中查询不到用户信息，则抛出未登录异常
        ThrowUtils.throwIf(currentUser == null, new BusinessException(ErrorCode.NOT_LOGIN_ERROR));

        // 返回查询到的用户信息
        return currentUser;
    }

    @Override
    public boolean LogoutUser(HttpServletRequest request) {
        // 先判断是否已登录
        User currentUser = (User)request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        // 如果会话中没有用户登录状态或用户ID为空，则抛出未登录异常
        ThrowUtils.throwIf(currentUser == null, new BusinessException(ErrorCode.OPERATION_ERROR, "未登录"));
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    /**
     * 脱敏登录用户信息
     * 该方法用于将用户实体对象转换为登录用户视图对象，以用于界面展示
     * 主要目的是防止敏感信息泄露，确保用户信息安全
     *
     * @param user 用户实体对象，包含完整的用户信息
     * @return UserLoginVO 登录用户视图对象，包含脱敏后的用户信息
     */
    @Override
    public UserLoginVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserLoginVO userLoginVO =new UserLoginVO();
        BeanUtils.copyProperties(user, userLoginVO);
        return userLoginVO;
    }

    /**
     * 脱敏用户信息
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取脱敏的用户列表视图对象
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    /**
     * 加密算法
     * @param password
     * @return
     */
    @Override
    public String getEncryptPassword(String password) {
        String salt = "yoyo";
        return DigestUtils.md5DigestAsHex((salt + password).getBytes());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryDTO userQueryDTO) {
        if (userQueryDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryDTO.getId();
        String userAccount = userQueryDTO.getUserAccount();
        String userName = userQueryDTO.getUserName();
        String userProfile = userQueryDTO.getUserProfile();
        String userRole = userQueryDTO.getUserRole();
        String sortField = userQueryDTO.getSortField();
        String sortOrder = userQueryDTO.getSortOrder();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }
}