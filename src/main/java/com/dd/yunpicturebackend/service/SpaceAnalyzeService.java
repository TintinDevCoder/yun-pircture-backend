package com.dd.yunpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dd.yunpicturebackend.model.dto.space.SpaceAddRequest;
import com.dd.yunpicturebackend.model.dto.space.SpaceQueryRequest;
import com.dd.yunpicturebackend.model.dto.space.analyze.SpaceAnalyzeRequest;
import com.dd.yunpicturebackend.model.entity.Space;
import com.dd.yunpicturebackend.model.entity.User;
import com.dd.yunpicturebackend.model.vo.space.SpaceVO;
import com.dd.yunpicturebackend.model.vo.space.analyze.SpaceUsageAnalyzeResponse;

import javax.servlet.http.HttpServletRequest;

/**
* @author DELL
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2025-02-01 16:01:58
*/
public interface SpaceAnalyzeService extends IService<Space> {
    /**
     * 获取空间使用情况分析
     * @param spaceAnalyzeRequest
     * @param loginUser
     * @return
     */
    SpaceUsageAnalyzeResponse getSpaceUsageAnalyze(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser);


    /**
     * 校验空间分析权限
     * @param spaceAnalyzeRequest
     * @param loginUser
     */
    public void checkSpaceAnalyzeAuth(SpaceAnalyzeRequest spaceAnalyzeRequest, User loginUser);

}
