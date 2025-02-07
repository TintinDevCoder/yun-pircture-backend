package com.dd.yunpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dd.yunpicturebackend.model.dto.space.SpaceAddRequest;
import com.dd.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import com.dd.yunpicturebackend.model.dto.space.analyze.SpaceAnalyzeRequest;
import com.dd.yunpicturebackend.model.entity.Space;
import com.dd.yunpicturebackend.model.entity.User;
import com.dd.yunpicturebackend.model.vo.space.SpaceVO;
import com.baomidou.mybatisplus.extension.service.IService;


import javax.servlet.http.HttpServletRequest;

/**
* @author DELL
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-02-01 16:01:58
*/
public interface SpaceService extends IService<Space> {
    /**
     * 创建空间
     * @param spaceAddRequest
     * @return
     */
    public long addSpace(SpaceAddRequest spaceAddRequest, User loginUser);
    /**
     * 校验数据
     * @param space
     */
    public void validSpace(Space space, boolean add);

    /**
     * 获取空间包装类（单条）
     * @param space
     * @param request
     * @return
     */
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 获取空间包装类（分页）
     * @param spacePage
     * @param request
     * @return
     */
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);

    /**
     * 获取查询对象
     * @param spaceQueryRequest
     * @return
     */
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 根据空间级别填充空间参数
     * @param space
     */
    public void fillSpaceBySoaceLevel(Space space);

    /**
     * 校验空间权限
     * @param loginUser
     * @param space
     */
    void checkSpaceAuth(User loginUser, Space space);
}
