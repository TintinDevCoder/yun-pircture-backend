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
import com.dd.spring.yunpicturebackend.enums.PictureReviewStatusEnum;
import com.dd.spring.yunpicturebackend.exception.BusinessException;
import com.dd.spring.yunpicturebackend.exception.ErrorCode;
import com.dd.spring.yunpicturebackend.exception.ThrowUtils;
import com.dd.spring.yunpicturebackend.manager.CacheManager;
import com.dd.spring.yunpicturebackend.model.dto.picture.*;
import com.dd.spring.yunpicturebackend.model.entity.Picture;
import com.dd.spring.yunpicturebackend.model.entity.User;
import com.dd.spring.yunpicturebackend.model.vo.picture.PictureTagCategory;
import com.dd.spring.yunpicturebackend.model.vo.picture.PictureVO;
import com.dd.spring.yunpicturebackend.service.PictureService;
import com.dd.spring.yunpicturebackend.service.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/picture")
@Slf4j
public class PictureController {
    @Resource
    private UserService userService;
    @Resource
    private PictureService pictureService;
    @Resource
    private CacheManager cacheManager;

    /**
     * 本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();

    //用户
    /**
     * 根据id查询图片（用户用、脱敏）
     * @param id
     * @param request
     * @return
     */
    @Cacheable(cacheNames = "setmealDishCache", key = "#id")
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(@RequestParam("id") Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        // 查询缓存
        String cacheTag = "getPictureVOById";
        String cacheValue = (String) cacheManager.getCacheData(id, cacheTag);
        if (cacheValue != null) {
            //缓存命中，缓存结果返回
            PictureVO cachePage = JSONUtil.toBean(cacheValue, PictureVO.class);
            return ResultUtils.success(cachePage);
        }
        //查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        //仅返回审核后的
        ThrowUtils.throwIf(picture.getReviewStatus() != PictureReviewStatusEnum.PASS.getValue(), new BusinessException(ErrorCode.FORBIDDEN_ERROR, "图片资源错误"));
        //存入缓存
        //设置缓存过期时间(5-10min),防止缓存雪崩
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        cacheManager.setCacheData(id, picture, cacheTag, cacheExpireTime);
        //返回封装类
        return ResultUtils.success(pictureService.getPictureVO(picture, request));
    }
    /**
     * 分页条件获取图片封装类列表（用户用、脱敏）
     * @param pictureQueryRequest
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<PictureVO>> listPictureVOByPage(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                           HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size >= 20 || size < 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        // 仅限审核通过的返回
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size)
                ,pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> picturePageVOList = pictureService.getPictureVOPage(picturePage, request);
        return ResultUtils.success(picturePageVOList);
    }

    /**
     * 分页条件获取图片封装类列表（用户用、脱敏、含有redis缓存）
     * @param pictureQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo/cache")
    public BaseResponse<Page<PictureVO>> listPictureVOByPageWithCache(@RequestBody PictureQueryRequest pictureQueryRequest,
                                                             HttpServletRequest request) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20 || size < 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        // 仅限审核通过的可以查看
        pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        // 查询缓存
        String cacheTag = "listPictureVOByPage";
        String cacheValue = (String) cacheManager.getCacheData(pictureQueryRequest, cacheTag);
        if (cacheValue != null) {
            //缓存命中，缓存结果返回
            Page<PictureVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
            return ResultUtils.success(cachePage);
        }

        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size)
                ,pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> picturePageVOList = pictureService.getPictureVOPage(picturePage, request);
        //存入缓存
        //设置缓存过期时间(5-10min),防止缓存雪崩
        int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
        cacheManager.setCacheData(pictureQueryRequest, picturePageVOList, cacheTag, cacheExpireTime);

        return ResultUtils.success(picturePageVOList);
    }
    /**
     * 编辑图片（用户）
     * @param pictureEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditRequest == null ||pictureEditRequest.getId() <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        //实体类转化为DTO
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        pictureService.validPicture(picture);
        User loginUser = userService.getLoginUser(request);
        //补充审核参数
        pictureService.fillReviewParams(picture, loginUser);
        // 判断图片是否存在
        QueryWrapper qw = new QueryWrapper<>().eq("id", pictureEditRequest.getId());
        boolean exists = pictureService.exists(qw);
        ThrowUtils.throwIf(!exists, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        // 仅本人或管理员可编辑
        Picture oldPicture = pictureService.getById(pictureEditRequest.getId());
        if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库，更新数据
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, new BusinessException(ErrorCode.OPERATION_ERROR));
        return ResultUtils.success(true);
    }
    //管理员

    /**
     * 更新图片（管理员）
     * @param pictureUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updatePicture(@RequestBody PictureUpdateRequest pictureUpdateRequest,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUpdateRequest == null ||pictureUpdateRequest.getId() <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        //实体类转化为DTO
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureUpdateRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        // 数据校验
        pictureService.validPicture(picture);
        //判断是否存在
        QueryWrapper qw = new QueryWrapper<>().eq("id", pictureUpdateRequest.getId());
        boolean exists = pictureService.exists(qw);
        ThrowUtils.throwIf(!exists, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        User loginUser = userService.getLoginUser(request);
        //补充审核参数
        pictureService.fillReviewParams(picture, loginUser);
        //操作数据库，更新数据
        boolean result = pictureService.updateById(picture);
        ThrowUtils.throwIf(!result, new BusinessException(ErrorCode.OPERATION_ERROR));
        return ResultUtils.success(true);
    }
    /**
     * 根据id查询图片（管理员用、不脱敏）
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Picture> getPictureById(@RequestParam("id") Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        //查询数据库
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        //返回封装类
        return ResultUtils.success(picture);
    }

    /**
     * 分页条件获取图片列表（管理员用、不脱敏）
     * @param pictureQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Picture>> listPictureByPage(@RequestBody PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        //查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size)
                ,pictureService.getQueryWrapper(pictureQueryRequest));
        return ResultUtils.success(picturePage);
    }

    /**
     * 审核图片
     * @param pictureReviewRequest
     * @param request
     * @return
     */
    @PostMapping("/review")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> doPictureReview(@RequestBody PictureReviewRequest pictureReviewRequest,
                                                         HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        pictureService.doPictureReview(pictureReviewRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 批量抓取并上传图片
     */
    @PostMapping("/upload/batch")
    public BaseResponse<Integer> uploadPictureByBatch(
            @RequestBody PictureUploadByBatchRequest pictureUploadByBatchRequest,
            HttpServletRequest request) {
        ThrowUtils.throwIf(pictureUploadByBatchRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        Integer uploadCount = pictureService.uploadPictureByBatch(pictureUploadByBatchRequest, loginUser);
        return ResultUtils.success(uploadCount);
    }
    //公用
    /**
     * 上传图片（可重新上传）
     */
    @PostMapping("/upload")
    public BaseResponse<PictureVO> uploadPicture(
            @RequestPart("file") MultipartFile multipartFile,
            PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(multipartFile, pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }
    /**
     * 通过 url 上传图片（可重新上传）
     */
    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(pictureUploadRequest.getFileUrl(), pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }
    /**
     * 删除图片
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest
    , HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        User loginUser = userService.getLoginUser(request);
        Long id = deleteRequest.getId();
        Picture oldPicture = pictureService.getById(id);
        //图片不存在，无法删除
        ThrowUtils.throwIf(oldPicture == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        //当前不是创建图片的用户或管理员
        ThrowUtils.throwIf(!oldPicture.getUserId().equals(loginUser.getId()) && ! userService.isAdmin(loginUser), new BusinessException(ErrorCode.NO_AUTH_ERROR));
        //操作数据库，删除
        try{
            boolean result = pictureService.removeById(id);
            ThrowUtils.throwIf(!result, new BusinessException(ErrorCode.OPERATION_ERROR));
        }catch (Exception e){
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return ResultUtils.success(true);
    }

    /**
     * 获取标签
     * @return
     */
    @GetMapping("/tag_category")
    public BaseResponse<PictureTagCategory> listPictureTagCategory() {
        PictureTagCategory pictureTagCategory = new PictureTagCategory();
        List<String> tagList = Arrays.asList("热门", "搞笑", "生活", "高清", "艺术", "校园", "背景", "简历", "创意");
        List<String> categoryList = Arrays.asList("模板", "电商", "表情包", "素材", "海报");
        pictureTagCategory.setTagList(tagList);
        pictureTagCategory.setCategoryList(categoryList);
        return ResultUtils.success(pictureTagCategory);
    }




}
