package com.dd.yunpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dd.yunpicturebackend.constant.UserConstant;
import com.dd.yunpicturebackend.enums.SpaceLevelEnum;
import com.dd.yunpicturebackend.exception.ErrorCode;
import com.dd.yunpicturebackend.exception.ThrowUtils;
import com.dd.yunpicturebackend.model.dto.space.SpaceAddRequest;
import com.dd.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import com.dd.yunpicturebackend.model.entity.Space;
import com.dd.yunpicturebackend.model.entity.User;
import com.dd.yunpicturebackend.model.vo.space.SpaceVO;
import com.dd.yunpicturebackend.model.vo.user.UserVO;
import com.dd.yunpicturebackend.service.SpaceService;
import com.dd.yunpicturebackend.mapper.SpaceMapper;
import com.dd.yunpicturebackend.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
* @author DELL
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2025-02-01 16:01:58
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{
    @Resource
    private SpaceMapper spaceMapper;
    @Resource
    private UserService userService;
    @Resource
    private TransactionTemplate transactionTemplate;
    Map<Long, Object> lockMap = new ConcurrentHashMap<>();
    /**
     * 创建空间
     * @param spaceAddRequest
     * @param loginUser
     * @return
     */
    @Override
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser) {
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddRequest, space);
        // 1.校验权限，非管理员只能创建普通级别的空间
        String userRole = loginUser.getUserRole();
        if (!userRole.equals(UserConstant.ADMIN_ROLE) && space.getSpaceLevel() != SpaceLevelEnum.COMMON.getValue()) {
            ThrowUtils.throwIf(space.getSpaceLevel() != SpaceLevelEnum.COMMON.getValue(), ErrorCode.NO_AUTH_ERROR, "非管理员只能创建普通级别的空间");
        }
        // 2.校验参数
        this.validSpace(space, true);
        // 3.填充参数默认值
        if (StrUtil.isBlank(space.getSpaceName())) {
            space.setSpaceName("默认空间");
        }
        if (space.getSpaceLevel() == null) {
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        this.fillSpaceBySoaceLevel(space);
        Long loginUserId = loginUser.getId();
        space.setUserId(loginUserId);
        // 4.同一用户只能创建一个私有空间
        Object lock = lockMap.computeIfAbsent(loginUserId, key -> new Object());
        synchronized (lock) {
            try {
                // 数据库操作
                Long newSpaceId = transactionTemplate.execute(status -> {
                    // 判断是否已经存在私有空间
                    boolean exists = this.lambdaQuery()
                            .eq(Space::getUserId, loginUserId)
                            .exists();
                    // 如果已经有空间，就不能创建
                    if (exists) {
                        ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "已经存在私有空间");
                    }
                    //创建
                    boolean result = this.save(space);
                    ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建空间失败");
                    //返回写入的id
                    return space.getId();
                });
                return newSpaceId;
            } finally {
                // 防止内存泄漏
                lockMap.remove(loginUserId);
            }
        }
    }

    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = space.getId();
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        //如果是创建校验
        if (add) {
            ThrowUtils.throwIf(StrUtil.isBlank(spaceName), ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            ThrowUtils.throwIf(spaceLevelEnum == null, ErrorCode.PARAMS_ERROR, "空间级别不能为空");
        }
        //修改或新增数据时，空间名称进行校验
        ThrowUtils.throwIf(StrUtil.isBlank(spaceName) || spaceName.length() > 30, ErrorCode.PARAMS_ERROR, "空间名称不能过程或为空");
        //修改或新增数据时，空间级别进行校验
        ThrowUtils.throwIf(spaceLevel != null && spaceLevelEnum == null, ErrorCode.PARAMS_ERROR, "空间级别不存在");
    }

    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 关联查询用户信息
        Long userId = space.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            spaceVO.setUser(userVO);
        }
        return spaceVO;
    }

    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        List<Space> spaceList = spacePage.getRecords();
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (CollUtil.isEmpty(spaceList)) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        List<SpaceVO> spaceVOList = spaceList.stream().map(SpaceVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = spaceList.stream().map(Space::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        spaceVOList.forEach(spaceVO -> {
            Long userId = spaceVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            spaceVO.setUser(userService.getUserVO(user));
        });
        spaceVOPage.setRecords(spaceVOList);
        return spaceVOPage;
    }

    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        Long spaceId = spaceQueryRequest.getId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        Long spaceUserId = spaceQueryRequest.getUserId();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();

        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "id", spaceId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceUserId), "userId", spaceUserId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);

        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public void fillSpaceBySoaceLevel(Space space) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        //空间级别进行校验
        ThrowUtils.throwIf(spaceLevel == null, ErrorCode.PARAMS_ERROR, "空间级别不存在");
        ThrowUtils.throwIf((spaceLevel == null) || (spaceLevel != null && spaceLevelEnum == null), ErrorCode.PARAMS_ERROR, "空间级别不存在");
        Long spaceMaxSize = space.getMaxSize();
        Long spaceMaxCount = space.getMaxCount();

        long maxCount = spaceLevelEnum.getMaxCount();
        if (spaceMaxSize == null) {
            space.setMaxSize(spaceLevelEnum.getMaxSize());
        }
        if (spaceMaxCount == null) {
            space.setMaxCount(spaceLevelEnum.getMaxCount());
        }
    }

}




