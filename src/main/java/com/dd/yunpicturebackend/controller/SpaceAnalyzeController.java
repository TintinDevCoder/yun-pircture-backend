package com.dd.yunpicturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dd.yunpicturebackend.annotation.AuthCheck;
import com.dd.yunpicturebackend.common.BaseResponse;
import com.dd.yunpicturebackend.common.DeleteRequest;
import com.dd.yunpicturebackend.common.ResultUtils;
import com.dd.yunpicturebackend.constant.UserConstant;
import com.dd.yunpicturebackend.enums.SpaceLevelEnum;
import com.dd.yunpicturebackend.exception.BusinessException;
import com.dd.yunpicturebackend.exception.ErrorCode;
import com.dd.yunpicturebackend.exception.ThrowUtils;
import com.dd.yunpicturebackend.model.dto.space.*;
import com.dd.yunpicturebackend.model.dto.space.analyze.SpaceAnalyzeRequest;
import com.dd.yunpicturebackend.model.entity.Picture;
import com.dd.yunpicturebackend.model.entity.Space;
import com.dd.yunpicturebackend.model.entity.User;
import com.dd.yunpicturebackend.model.vo.space.SpaceVO;
import com.dd.yunpicturebackend.model.vo.space.analyze.SpaceUsageAnalyzeResponse;
import com.dd.yunpicturebackend.service.PictureService;
import com.dd.yunpicturebackend.service.SpaceAnalyzeService;
import com.dd.yunpicturebackend.service.SpaceService;
import com.dd.yunpicturebackend.service.UserService;
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
@RequestMapping("/space/analyze")
@Slf4j
public class SpaceAnalyzeController {
    @Resource
    private UserService userService;
    @Resource
    private SpaceService spaceService;
    @Resource
    private SpaceAnalyzeService spaceAnalyzeService;
    @Autowired
    private PictureService pictureService;

    /**
     * 空间使用情况分析
     * @param spaceAnalyzeRequest
     * @param request
     * @return
     */
    @GetMapping("/usage")
    public BaseResponse getSpaceUsageAnalyze(SpaceAnalyzeRequest spaceAnalyzeRequest,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(spaceAnalyzeRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        SpaceUsageAnalyzeResponse spaceUsageAnalyze = spaceAnalyzeService.getSpaceUsageAnalyze(spaceAnalyzeRequest, loginUser);
        return ResultUtils.success(spaceUsageAnalyze);
    }


}
