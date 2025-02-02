package com.dd.spring.yunpicturebackend.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import com.dd.spring.yunpicturebackend.annotation.AuthCheck;
import com.dd.spring.yunpicturebackend.common.BaseResponse;
import com.dd.spring.yunpicturebackend.common.DeleteRequest;
import com.dd.spring.yunpicturebackend.common.ResultUtils;
import com.dd.spring.yunpicturebackend.constant.UserConstant;
import com.dd.spring.yunpicturebackend.enums.SpaceLevelEnum;
import com.dd.spring.yunpicturebackend.exception.BusinessException;
import com.dd.spring.yunpicturebackend.exception.ErrorCode;
import com.dd.spring.yunpicturebackend.exception.ThrowUtils;
import com.dd.spring.yunpicturebackend.model.dto.space.*;
import com.dd.spring.yunpicturebackend.model.entity.Picture;
import com.dd.spring.yunpicturebackend.model.entity.Space;
import com.dd.spring.yunpicturebackend.model.entity.User;
import com.dd.spring.yunpicturebackend.model.vo.space.SpaceVO;
import com.dd.spring.yunpicturebackend.service.PictureService;
import com.dd.spring.yunpicturebackend.service.SpaceService;
import com.dd.spring.yunpicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/space")
@Slf4j
public class SpaceController {
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Autowired
    private PictureService pictureService;
    //管理员

    /**
     * 更新空间（管理员）
     * @param spaceUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUpdateRequest == null ||spaceUpdateRequest.getId() <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        //实体类转化为DTO
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        // 数据校验
        spaceService.validSpace(space, false);
        //判断是否存在
        QueryWrapper qw = new QueryWrapper<>().eq("id", spaceUpdateRequest.getId());
        boolean exists = spaceService.exists(qw);
        ThrowUtils.throwIf(!exists, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        User loginUser = userService.getLoginUser(request);
        //自动填充数据
        spaceService.fillSpaceBySoaceLevel(space);
        //操作数据库，更新数据
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, new BusinessException(ErrorCode.OPERATION_ERROR));
        return ResultUtils.success(true);
    }
    /**
     * 根据id查询空间（管理员用、不脱敏）
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Space> getSpaceById(@RequestParam("id") Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        //查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        //返回封装类
        return ResultUtils.success(space);
    }

    /**
     * 分页条件获取空间列表（管理员用、不脱敏）
     * @param spaceQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        //查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size)
                ,spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spacePage);
    }

    //公用
    /**
     * 添加空间（用户）
     * @param spaceAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest spaceAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAddRequest == null, new BusinessException(ErrorCode.PARAMS_ERROR));
        //获取登录用户
        User loginUser = userService.getLoginUser(request);
        //创建空间
        long spaceId = spaceService.addSpace(spaceAddRequest, loginUser);
        return ResultUtils.success(spaceId);
    }

    /**
     * 根据id查询空间（用户用、脱敏）
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<SpaceVO> getSpaceVOById(@RequestParam("id") Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        //查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        //返回封装类
        return ResultUtils.success(spaceService.getSpaceVO(space, request));
    }
    /**
     * 分页条件获取空间封装类列表（用户用、脱敏）
     * @param spaceQueryRequest
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                         HttpServletRequest request) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size >= 20 || size < 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size)
                ,spaceService.getQueryWrapper(spaceQueryRequest));
        Page<SpaceVO> spacePageVOList = spaceService.getSpaceVOPage(spacePage, request);
        return ResultUtils.success(spacePageVOList);
    }

    /**
     * 编辑空间（用户）
     * @param spaceEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest,
                                           HttpServletRequest request) {
        ThrowUtils.throwIf(spaceEditRequest == null ||spaceEditRequest.getId() <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        //实体类转化为DTO
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);
        // 设置编辑时间
        space.setEditTime(new Date());
        // 数据校验
        spaceService.validSpace(space, false);
        User loginUser = userService.getLoginUser(request);
        // 判断空间是否存在
        QueryWrapper qw = new QueryWrapper<>().eq("id", spaceEditRequest.getId());
        boolean exists = spaceService.exists(qw);
        ThrowUtils.throwIf(!exists, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        // 仅本人或管理员可编辑
        Space oldSpace = spaceService.getById(spaceEditRequest.getId());
        if (!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库，更新数据
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, new BusinessException(ErrorCode.OPERATION_ERROR));
        return ResultUtils.success(true);
    }
    /**
     * 删除空间
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest
    , HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        User loginUser = userService.getLoginUser(request);
        Long id = deleteRequest.getId();
        Space oldSpace = spaceService.getById(id);
        //空间不存在，无法删除
        ThrowUtils.throwIf(oldSpace == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        //当前不是创建空间的用户或管理员
        ThrowUtils.throwIf(!oldSpace.getUserId().equals(loginUser.getId()) && ! userService.isAdmin(loginUser), new BusinessException(ErrorCode.NO_AUTH_ERROR));
        //开启事务
        //操作数据库，删除
        transactionTemplate.execute(status -> {
            //删除空间
            boolean result = spaceService.removeById(id);
            ThrowUtils.throwIf(!result, new BusinessException(ErrorCode.OPERATION_ERROR));
            //删除空间下的图片
            pictureService.lambdaUpdate().eq(Picture::getSpaceId, id).remove();
            return true;
        });
        return ResultUtils.success(true);
    }

    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values()) // 获取所有枚举
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }


}
