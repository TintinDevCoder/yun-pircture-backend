package com.dd.yunpicturebackend.controller;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dd.yunpicturebackend.annotation.AuthCheck;
import com.dd.yunpicturebackend.api.aliyunai.AliYunAiApi;
import com.dd.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.dd.yunpicturebackend.api.aliyunai.model.GetOutPaintingTaskResponse;
import com.dd.yunpicturebackend.api.imagesearch.ImageSearchApiFacade;
import com.dd.yunpicturebackend.api.imagesearch.model.ImageSearchResult;
import com.dd.yunpicturebackend.common.BaseResponse;
import com.dd.yunpicturebackend.common.DeleteRequest;
import com.dd.yunpicturebackend.common.ResultUtils;
import com.dd.yunpicturebackend.constant.UserConstant;
import com.dd.yunpicturebackend.enums.PictureReviewStatusEnum;
import com.dd.yunpicturebackend.enums.UserRoleEnum;
import com.dd.yunpicturebackend.exception.BusinessException;
import com.dd.yunpicturebackend.exception.ErrorCode;
import com.dd.yunpicturebackend.exception.ThrowUtils;
import com.dd.yunpicturebackend.manager.CacheManager;
import com.dd.yunpicturebackend.manager.auth.SpaceUserAuthManager;
import com.dd.yunpicturebackend.manager.auth.StpKit;
import com.dd.yunpicturebackend.manager.auth.annotation.SaSpaceCheckPermission;
import com.dd.yunpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import com.dd.yunpicturebackend.model.dto.picture.*;
import com.dd.yunpicturebackend.model.dto.picture.*;
import com.dd.yunpicturebackend.model.entity.Picture;
import com.dd.yunpicturebackend.model.entity.SharePicture;
import com.dd.yunpicturebackend.model.entity.Space;
import com.dd.yunpicturebackend.model.entity.User;
import com.dd.yunpicturebackend.model.vo.picture.PictureTagCategory;
import com.dd.yunpicturebackend.model.vo.picture.PictureVO;
import com.dd.yunpicturebackend.model.vo.picture.SharePictureVO;
import com.dd.yunpicturebackend.model.vo.user.UserVO;
import com.dd.yunpicturebackend.service.PictureService;
import com.dd.yunpicturebackend.service.SharePictureService;
import com.dd.yunpicturebackend.service.SpaceService;
import com.dd.yunpicturebackend.service.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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
    private SharePictureService sharePictureService;
    @Resource
    private CacheManager cacheManager;
    @Resource
    private SpaceService spaceService;
    @Resource
    private AliYunAiApi aliYunAiApi;
    /**
     * 本地缓存
     */
    private final Cache<String, String> LOCAL_CACHE =
            Caffeine.newBuilder().initialCapacity(1024)
                    .maximumSize(10000L)
                    // 缓存 5 分钟移除
                    .expireAfterWrite(5L, TimeUnit.MINUTES)
                    .build();
    private SpaceUserAuthManager spaceUserAuthManager;
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


    //公用接口

    /**
     * 上传图片（可重新上传）
     */
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
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
    /**
     * 通过 url 上传图片（可重新上传）
     */
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_UPLOAD)
    @PostMapping("/upload/url")
    public BaseResponse<PictureVO> uploadPictureByUrl(
            @RequestBody PictureUploadRequest pictureUploadRequest,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        PictureVO pictureVO = pictureService.uploadPicture(pictureUploadRequest.getFileUrl(), pictureUploadRequest, loginUser);
        return ResultUtils.success(pictureVO);
    }

    /**
     * 根据id查询图片（用户用、脱敏）
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<PictureVO> getPictureVOById(@RequestParam("id") Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        PictureVO result = new PictureVO();
        Picture picture = new Picture();
        //构建缓存的key
        String cacheKey = String.format("yunpicture:%s:%s", "getPictureVOById", id);
        // 查询缓存
        String cacheValue = (String) cacheManager.getCacheData(cacheKey);
        if (cacheValue != null) {
            //缓存命中，使用缓存结果
            picture = JSONUtil.toBean(cacheValue, Picture.class);
            ThrowUtils.throwIf(picture == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR));

        }else {
            //查询数据库
            picture = pictureService.getById(id);
        }
        ThrowUtils.throwIf(picture == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        //封装类
        result = pictureService.getPictureVO(picture, request);
        //校验空间权限
        Long spaceId = result.getSpaceId();
        Space space = null;
        if (spaceId != null) {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, new BusinessException(ErrorCode.NO_AUTH_ERROR));
            //已经改为使用注解鉴权
            //pictureService.checkPictureAuth(picture, loginUser);
            //User loginUser = userService.getLoginUser(request);
            space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        User loginUser = userService.getLoginUser(request);
        //获取权限列表
        List<String> permissionList = spaceUserAuthManager.getPermissionList(space, loginUser);
        result.setPermissionList(permissionList);
        //仅返回审核后的
        ThrowUtils.throwIf(picture.getReviewStatus() != PictureReviewStatusEnum.PASS.getValue(), new BusinessException(ErrorCode.FORBIDDEN_ERROR, "图片资源错误"));
        if (cacheValue == null) {
            //存入缓存
            //设置缓存过期时间(5-10min),防止缓存雪崩
            int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
            cacheManager.setCacheData(cacheKey, picture, cacheExpireTime);
        }
        //返回封装类
        return ResultUtils.success(result);
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

        // 校验空间权限
        Long spaceId = pictureQueryRequest.getSpaceId();
        if (spaceId == null) {
            // 普通用户仅限审核通过的可以查看
            pictureQueryRequest.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            pictureQueryRequest.setNullSpaceId(true); // 无空间id代表公共图片，此参数设为true
        }else {
            boolean hasPermission = StpKit.SPACE.hasPermission(SpaceUserPermissionConstant.PICTURE_VIEW);
            ThrowUtils.throwIf(!hasPermission, new BusinessException(ErrorCode.NO_AUTH_ERROR));
            // 设置了空间为查询条件，需要校验权限
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }
        //公共空间才查询缓存
        String cacheKey = null;
        String cacheValue = null;
        if (spaceId == null) {
            // 构建缓存的key
            String queryCondition = JSONUtil.toJsonStr(pictureQueryRequest);
            String hashKey = DigestUtils.md5DigestAsHex(queryCondition.getBytes());
            cacheKey = String.format("yunpicture:%s:%s", "listPictureVOByPage", hashKey);
            // 查询缓存
            cacheValue = (String) cacheManager.getCacheData(cacheKey);
            if (cacheValue != null) {
                //缓存命中，缓存结果返回
                Page<PictureVO> cachePage = JSONUtil.toBean(cacheValue, Page.class);
                return ResultUtils.success(cachePage);
            }
        }

        // 查询数据库
        Page<Picture> picturePage = pictureService.page(new Page<>(current, size)
                ,pictureService.getQueryWrapper(pictureQueryRequest));
        Page<PictureVO> picturePageVOList = pictureService.getPictureVOPage(picturePage, request);

        if (spaceId == null) {
            //存入缓存
            //设置缓存过期时间(5-10min),防止缓存雪崩
            int cacheExpireTime = 300 + RandomUtil.randomInt(0, 300);
            cacheManager.setCacheData(cacheKey, picturePageVOList, cacheExpireTime);
        }


        return ResultUtils.success(picturePageVOList);
    }
    /**
     * 编辑图片（用户）
     * @param pictureEditRequest
     * @param request
     * @return
     */
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    @PostMapping("/edit")
    public BaseResponse<Boolean> editPicture(@RequestBody PictureEditRequest pictureEditRequest,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditRequest == null || pictureEditRequest.getId() <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        User loginUser = userService.getLoginUser(request);
        Boolean result = pictureService.editPicture(pictureEditRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 批量编辑图片
     * @param pictureEditByBatchRequest
     * @param request
     * @return
     */
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    @PostMapping("/edit/batch")
    public BaseResponse<Boolean> editPictureByBatch(@RequestBody PictureEditByBatchRequest pictureEditByBatchRequest,
                                                    HttpServletRequest request) {
        ThrowUtils.throwIf(pictureEditByBatchRequest == null, new BusinessException(ErrorCode.PARAMS_ERROR));
        User loginUser = userService.getLoginUser(request);
        pictureService.editPictureByBatch(pictureEditByBatchRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 创建Ai扩图任务
     * @param createPictureOutPaintingTaskRequest
     * @param request
     * @return
     */
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_EDIT)
    @PostMapping("/out_pating/create_task")
    public BaseResponse<CreateOutPaintingTaskResponse> createPictureOutPaintingTask(@RequestBody CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest
            , HttpServletRequest request) {
        ThrowUtils.throwIf(createPictureOutPaintingTaskRequest == null || createPictureOutPaintingTaskRequest.getPictureId() == null || createPictureOutPaintingTaskRequest.getPictureId() <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        User loginUser = userService.getLoginUser(request);
        CreateOutPaintingTaskResponse pictureOutPaintingTask = pictureService.createPictureOutPaintingTask(createPictureOutPaintingTaskRequest, loginUser);
        return ResultUtils.success(pictureOutPaintingTask);
    }

    /**
     * 查看Ai扩图任务
     * @param taskId
     * @return
     */
    @GetMapping("/out_pating/get_task")
    public BaseResponse<GetOutPaintingTaskResponse> getPictureOutPaintingTask(String taskId) {
        ThrowUtils.throwIf(StrUtil.isBlank(taskId), new BusinessException(ErrorCode.PARAMS_ERROR));
        // 获取任务状态
        GetOutPaintingTaskResponse outPaintingTask = aliYunAiApi.getOutPaintingTask(taskId);
        return ResultUtils.success(outPaintingTask);
    }


    /**
     * 以图搜图
     */
    @PostMapping("/search/picture")
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(@RequestBody SearchPictureByPictureRequest searchPictureByPictureRequest) {
        ThrowUtils.throwIf(searchPictureByPictureRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = searchPictureByPictureRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        // 将图片转换为png格式（腾讯云提供）
        String url = picture.getUrl() + "?imageMogr2/format/png";
        // 获取以图搜图结果
        List<ImageSearchResult> imageSearchResults = ImageSearchApiFacade.searchImage(url);
        return ResultUtils.success(imageSearchResults);
    }
    /**
     * 按照颜色搜索图片(个人空间)
     */
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_VIEW)
    @PostMapping("/search/color")
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest searchPictureByColorRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(searchPictureByColorRequest == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = searchPictureByColorRequest.getSpaceId();
        String picColor = searchPictureByColorRequest.getPicColor();
        User loginUser = userService.getLoginUser(request);
        List<PictureVO> pictureVOS = pictureService.searchPictureByColor(spaceId, picColor, loginUser);
        return ResultUtils.success(pictureVOS);
    }
    /**
     * 按照颜色搜索图片（主页）
     */
    @PostMapping("/public/search/color")
    public BaseResponse<List<PictureVO>> searchPublicPictureByColor(String picColor) {
        List<PictureVO> pictureVOS = pictureService.searchPictureByColor(picColor);
        return ResultUtils.success(pictureVOS);
    }
    /**
     * 删除图片
     * @param deleteRequest
     * @param request
     * @return
     */
    @SaSpaceCheckPermission(value = SpaceUserPermissionConstant.PICTURE_DELETE)
    @PostMapping("/delete")
    public BaseResponse<Boolean> deletePicture(@RequestBody DeleteRequest deleteRequest
    , HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        User loginUser = userService.getLoginUser(request);
        Long id = deleteRequest.getId();
        pictureService.deletePicture(id, loginUser);
        return ResultUtils.success(true);
    }
    /**
     * 根据id查询分享图片(每天0点更新)
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/share/vo")
    public BaseResponse<SharePictureVO> getPictureVOBySharePictureId(@RequestParam("id") Long id, HttpServletRequest request) {
        //校验参数
        ThrowUtils.throwIf(id <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        //校验是否分享图片存在
        SharePicture sharePicture = sharePictureService.getById(id);
        ThrowUtils.throwIf(sharePicture == null, ErrorCode.NOT_FOUND_ERROR, "分享图片不存在");
        //封装类
        User loginUser = userService.getLoginUser(request);
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(loginUser, userVO);
        SharePictureVO sharePictureVO = new SharePictureVO();
        BeanUtils.copyProperties(sharePicture, sharePictureVO);
        sharePictureVO.setUser(userVO);
        //返回封装类
        return ResultUtils.success(sharePictureVO);
    }

    /**
     * 设置分享图片
     * @param url
     * @param request
     * @return
     */
    @PostMapping("/set/share")
    public BaseResponse<Boolean> setSharePictureById(@RequestParam("url") String url, HttpServletRequest request) {
        //校验参数
        ThrowUtils.throwIf(StrUtil.isBlank(url), new BusinessException(ErrorCode.PARAMS_ERROR));
        Long id = Long.parseLong(url.substring(url.lastIndexOf("/") + 1));
        ThrowUtils.throwIf(id == null || id <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        Picture picture = pictureService.getById(id);
        //校验是否存在
        ThrowUtils.throwIf(picture == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        User loginUser = userService.getLoginUser(request);
        String userRole = loginUser.getUserRole();
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(userRole);
        //校验权限
        ThrowUtils.throwIf(loginUser == null || (!loginUser.getId().equals(picture.getUserId()) && userRoleEnum != UserRoleEnum.ADMIN), new BusinessException(ErrorCode.NO_AUTH_ERROR));
        //判断是否已经分享
        boolean exists = sharePictureService.exists(new QueryWrapper<SharePicture>().eq("id", id));
        if (exists) {
            return ResultUtils.success(true);
        }
        //保存分享图片
        SharePicture sharePicture = new SharePicture();
        BeanUtils.copyProperties(picture, sharePicture);
        sharePicture.setCreateTime(new Date());
        sharePicture.setUpdateTime(new Date());
        sharePicture.setSaveNum(0L);
        sharePictureService.save(sharePicture);
        return ResultUtils.success(true);
    }

    /**
     * 保存分享图片
     * @param id
     * @param request
     * @return
     */
    @PostMapping("/save/share")
    public BaseResponse<Boolean> saveSharePicture(@RequestParam("id") Long id, HttpServletRequest request) {
        //校验参数
        ThrowUtils.throwIf(id == null || id <= 0, new BusinessException(ErrorCode.PARAMS_ERROR));
        //校验是否存在
        Picture picture = pictureService.getById(id);
        ThrowUtils.throwIf(picture == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, new BusinessException(ErrorCode.NO_AUTH_ERROR));
        //查看是否已经有空间
        Space exists = spaceService.lambdaQuery().eq(Space::getUserId, loginUser.getId()).one();
        ThrowUtils.throwIf(exists == null, new BusinessException(ErrorCode.OPERATION_ERROR, "请先创建空间"));
        Picture savePicture = new Picture();
        BeanUtils.copyProperties(picture, savePicture);
        savePicture.setSpaceId(exists.getId());
        savePicture.setUserId(loginUser.getId());
        savePicture.setCreateTime(new Date());
        savePicture.setUpdateTime(new Date());
        savePicture.setId(null);
        pictureService.save(savePicture);
        //更新保存次数
        SharePicture sharePicture = sharePictureService.getById(id);
        sharePictureService.lambdaUpdate().eq(SharePicture::getId, id).set(SharePicture::getSaveNum, sharePicture.getSaveNum() + 1).update();
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


    /**
     * 删除redis中的所有缓存
     */
    @GetMapping("/delcache")
    public void deleteCache() {
        cacheManager.deleteAllCache();
    }
}
