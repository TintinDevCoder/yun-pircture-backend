package com.dd.yunpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dd.yunpicturebackend.model.dto.spaceuser.SpaceUserAddRequest;
import com.dd.yunpicturebackend.model.dto.spaceuser.SpaceUserQueryRequest;
import com.dd.yunpicturebackend.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dd.yunpicturebackend.model.entity.User;
import com.dd.yunpicturebackend.model.vo.spaceuser.SpaceUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author DELL
* @description 针对表【space_user(空间成员用户关联)】的数据库操作Service
* @createDate 2025-02-07 17:57:11
*/
public interface SpaceUserService extends IService<SpaceUser> {

    /**
     * 创建空间成员
     * @param spaceAddRequest
     * @return
     */
    public long addSpaceUser(SpaceUserAddRequest spaceAddRequest);

    /**
     * 获取空间成员包装类（单条）
     * @param spaceUser
     * @param request
     * @return
     */
    public SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    /**
     * 获取空间成员包装类（列表）
     * @param spaceUserList
     * @return
     */
    public List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);

    /**
     * 获取查询对象
     * @param spaceUserQueryRequest
     * @return
     */
    public QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    /**
     * 校验空间成员
     * @param spaceUser
     */
    public void validSpaceUser(SpaceUser spaceUser, boolean add);
}
