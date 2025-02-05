package com.dd.yunpicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dd.yunpicturebackend.api.aliyunai.AliYunAiApi;
import com.dd.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.dd.yunpicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.dd.yunpicturebackend.constant.error.FileErrorConstant;
import com.dd.yunpicturebackend.enums.PictureReviewStatusEnum;
import com.dd.yunpicturebackend.exception.BusinessException;
import com.dd.yunpicturebackend.exception.ErrorCode;
import com.dd.yunpicturebackend.exception.ThrowUtils;
import com.dd.yunpicturebackend.manager.CacheManager;
import com.dd.yunpicturebackend.manager.CosManager;
import com.dd.yunpicturebackend.manager.upload.FilePictureUploadImpl;
import com.dd.yunpicturebackend.manager.upload.UrlPictureUploadImpl;
import com.dd.yunpicturebackend.model.dto.file.UploadPictureResult;
import com.dd.spring.yunpicturebackend.model.dto.picture.*;
import com.dd.yunpicturebackend.model.dto.picture.*;
import com.dd.yunpicturebackend.model.entity.Picture;
import com.dd.yunpicturebackend.model.entity.Space;
import com.dd.yunpicturebackend.model.entity.User;
import com.dd.yunpicturebackend.model.vo.picture.PictureVO;
import com.dd.yunpicturebackend.model.vo.user.UserVO;
import com.dd.yunpicturebackend.service.PictureService;
import com.dd.yunpicturebackend.mapper.PictureMapper;
import com.dd.yunpicturebackend.service.SpaceService;
import com.dd.yunpicturebackend.service.UserService;
import com.dd.yunpicturebackend.utils.ColorSimilarUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
* @author DELL
* @description 针对表【picture(图片)】的数据库操作Service实现
* @createDate 2025-01-26 09:33:03
*/
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture> implements PictureService{
    @Resource
    private FilePictureUploadImpl filePictureUpload;
    @Resource
    private UrlPictureUploadImpl urlPictureUpload;
    @Resource
    private UserService userService;
    @Autowired
    private CacheManager cacheManager;
    @Resource
    private CosManager cosManager;
    @Resource
    private SpaceService spaceService;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private AliYunAiApi aliYunAiApi;
    @Override
    public PictureVO uploadPicture(Object inputSource,
                                   PictureUploadRequest pictureUploadRequest,
                                   User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            //仅本人或管理员可编辑空间
            ThrowUtils.throwIf(!loginUser.getId().equals(space.getUserId()), ErrorCode.NO_AUTH_ERROR);
            //校验额度
            ThrowUtils.throwIf(space.getTotalCount() >= space.getMaxCount() || space.getTotalSize() >= space.getMaxSize(), ErrorCode.NO_AUTH_ERROR, "空间额度已满");
        }

        // 判断是新增还是删除
        Long pictureId = null;
        if (pictureUploadRequest.getId() != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // pictureId有值说明是更新图片
        // 如果是更新，判断图片是否存在
        Picture oldpicture = null;
        if (pictureId != null) {
            oldpicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldpicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
            //仅本人或管理员可编辑图片
            ThrowUtils.throwIf((oldpicture.getUserId() != loginUser.getId()) && !userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);
            // 校验空间是否一致
            // 没传 spaceId 说明为公共空间
            if (spaceId == null) {
                if (oldpicture.getSpaceId() != null) {
                    spaceId = oldpicture.getSpaceId();
                }
            }else {
                // 传了 spaceId 必须与原图片的空间id一致
                if (ObjUtil.notEqual(spaceId, oldpicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间不一致");
                }
            }
        }
        // 上传图片，得到图片信息
        // 按照用户 id 划分目录 -> 按照空间划分目录
        String uploadPathPrefix = String.format("public/%s",loginUser.getId());
        if (spaceId != null) {
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        //根据inputSource的类型区分上传方式
        UploadPictureResult uploadPictureResult;
        if (inputSource instanceof String) {
            uploadPictureResult = urlPictureUpload.uploadPicture(inputSource, uploadPathPrefix);
        }else {
            uploadPictureResult = filePictureUpload.uploadPicture(inputSource, uploadPathPrefix);
        }
        //构造入库的图片信息
        Picture picture = new Picture();
        BeanUtils.copyProperties(uploadPictureResult, picture);
        picture.setName(uploadPictureResult.getPicName());
        picture.setUserId(loginUser.getId());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        picture.setSpaceId(spaceId); //空间id
        picture.setPicColor(uploadPictureResult.getPicColor()); //图片主色调
        //支持外层传递图片名称
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picture.setName(pictureUploadRequest.getPicName());
        }
        //补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        if (pictureId != null) {
            //如果是更新，则需要补充Id和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        try {
            Long finalSpaceId = spaceId;
            Picture finalOldpicture = oldpicture;
            Long finalPictureId = pictureId;
            transactionTemplate.execute(status -> {
                //插入图片
                boolean result = this.saveOrUpdate(picture);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, FileErrorConstant.DATABASE_ERROR);
                //若为插入或更新私有空间图片，更新空间的使用额度
                // 计算旧图片的大小，如果 finalOldpicture 为 null，则默认为 0
                long oldPicSize = 0;
                boolean update;
                if (finalSpaceId != null) {
                    if (finalPictureId != null) {
                        //如果是更新，则删除老图片的存储
                        this.clearPictureFile(finalOldpicture);
                        oldPicSize = finalOldpicture.getPicSize();
                        //更新空间的使用额度
                        update = spaceService.lambdaUpdate()
                                .eq(Space::getId, finalSpaceId)
                                .setSql(String.valueOf("totalSize = totalSize + " + picture.getPicSize() + "-" + oldPicSize))
                                .update();
                    }else {
                        //更新空间的使用额度
                        update = spaceService.lambdaUpdate()
                                .eq(Space::getId, finalSpaceId)
                                .setSql("totalCount = totalCount + 1")
                                .setSql(String.valueOf("totalSize = totalSize + " + picture.getPicSize()))
                                .update();
                    }
                    if (!update) {
                        throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新空间额度失败");
                    }
                }
                return picture;
            });
        }catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR);
        }
        return PictureVO.objToVo(picture);
    }
    public Boolean deletePicture(Long id, User loginUser) {
        Picture oldPicture = this.getById(id);
        //图片不存在，无法删除
        ThrowUtils.throwIf(oldPicture == null, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        //校验权限
        this.checkPictureAuth(oldPicture, loginUser);
        //开启事务
        transactionTemplate.execute(status -> {
            //操作数据库，删除
            boolean result = this.removeById(id);
            ThrowUtils.throwIf(!result, new BusinessException(ErrorCode.OPERATION_ERROR));
            //删除老图片的存储
            this.clearPictureFile(oldPicture);
            //若为私有空间图片，更新空间的使用额度
            Long spaceId = oldPicture.getSpaceId();
            if (spaceId != null) {
                // 计算旧图片的大小，如果 finalOldpicture 为 null，则默认为 0
                long oldPicSize = oldPicture.getPicSize();
                //更新空间的使用额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, spaceId)
                        .setSql("totalCount = totalCount - 1")
                        .setSql(String.valueOf("totalSize = totalSize - " + oldPicSize))
                        .update();
                if (!update) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新空间额度失败");
                }
            }
            return true;
        });
        return true;
    }
    public Boolean editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        //实体类转化为DTO
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        //补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 判断图片是否存在
        QueryWrapper qw = new QueryWrapper<>().eq("id", pictureEditRequest.getId());
        boolean exists = this.exists(qw);
        ThrowUtils.throwIf(!exists, new BusinessException(ErrorCode.NOT_FOUND_ERROR));
        // 校验权限
        this.checkPictureAuth(this.getById(pictureEditRequest.getId()), loginUser);
        // 操作数据库，更新数据
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, new BusinessException(ErrorCode.OPERATION_ERROR));
        return result;
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Date reviewTime = pictureQueryRequest.getReviewTime();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("name", searchText)
                    .or()
                    .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        //>=
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        //<=
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 分页获取图片
     * @param picturePage
     * @param request
     * @return
     */
    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
        // 1. 关联查询用户信息
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }
    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    /**
     * 图片审核
     * @param pictureReviewRequest
     * @param loginUser
     */
    @Override
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        //校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        String reviewMessage = pictureReviewRequest.getReviewMessage();

        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        // 不允许请求重新设置图片为待审核
        ThrowUtils.throwIf(id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum), ErrorCode.PARAMS_ERROR);
        // 判断图片是否存在
        Picture oldpicture = this.getById(id);
        ThrowUtils.throwIf(oldpicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验审核状态是否重复
        ThrowUtils.throwIf(oldpicture.getReviewStatus().equals(reviewStatus), ErrorCode.PARAMS_ERROR);
        // 数据库操作
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureReviewRequest, picture);
        picture.setReviewerId(loginUser.getId());
        picture.setReviewTime(new Date());
        picture.setReviewMessage(reviewMessage);
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
    }

    /**
     * 填充审核参数
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser) || picture.getSpaceId() != null) {
            //管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewTime(new Date());
            picture.setReviewMessage("管理员自动过审");
        } else {
            //非管理员 无论是编辑还是创建都是待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    /**
     * 成功创建的图片数
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return
     */
    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        String searchText = pictureUploadByBatchRequest.getSearchText();
        // 格式化数量
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        String namePrefix;
        //名称前缀默认等于搜索关键词
        if(StrUtil.isNotBlank(pictureUploadByBatchRequest.getNamePrefix())) {
            namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        }else {
            namePrefix = searchText;
        }
        // 要抓取的地址
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isNull(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过: {}", fileUrl);
                continue;
            }
            // 处理图片上传地址，防止出现转义问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功, id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                //批量存储数据库发生变化较大、刷新首页分页查询缓存
                cacheManager.refreshAllCacheByPrefix("listPictureVOByPage");
                break;
            }
        }
        return uploadCount;
    }
    @Async //异步执行
    @Override
    public void clearPictureFile(Picture oldpicture) {
        // 判断该图片是否被多条记录使用（使用了秒传）
        String pictureUrl = oldpicture.getUrl();
        Long count = this.lambdaQuery()
                .eq(Picture::getUrl, oldpicture.getUrl())
                .count();
        if (count > 1) {
            return;
        }
        // 删除原始图片
        cosManager.deleteObject(pictureUrl);
        log.info("删除原始图片成功: {}", pictureUrl);
        //删除缩略图
        String thumbnailUrl = oldpicture.getThumbnailUrl();
        log.info("删除缩略图成功: {}", thumbnailUrl);
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(thumbnailUrl);
        }
    }

    @Override
    public void checkPictureAuth(Picture picture, User loginUser) {
        Long spaceId = picture.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    /**
     * 根据颜色搜索图片(个人空间)
     * @param spaceId
     * @param picColor
     * @param loginUser
     * @return
     */
    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        // 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null || loginUser == null || !space.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "空间不存在或无权限");
        // 查询该空间下的所有图片（必须要有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return CollUtil.newArrayList();
        }
        // 将颜色转换成主色调
        Color targetColor = Color.decode(picColor);
        // 计算相似度并排序
        List<Picture> pictureVOList = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    //没有主色调的图片就会默认排序到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    //计算相似度
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12)//最多返回12条
                .collect(Collectors.toList());
        // 返回结果
        return pictureVOList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
    }
    /**
     * 根据颜色搜索图片(主页)
     * @param picColor
     * @return
     */
    @Override
    public List<PictureVO> searchPictureByColor(String picColor) {
        // 校验参数
        ThrowUtils.throwIf(StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        // 查询该空间下的所有图片（必须要有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .isNull(Picture::getSpaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return CollUtil.newArrayList();
        }
        // 将颜色转换成主色调
        Color targetColor = Color.decode(picColor);
        // 计算相似度并排序
        List<Picture> pictureVOList = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    //没有主色调的图片就会默认排序到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    //计算相似度
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12)//最多返回12条
                .collect(Collectors.toList());
        // 返回结果
        return pictureVOList.stream().map(PictureVO::objToVo).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        //获取和校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR, "图片id列表不能为空");
        //校验空间权限
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null || !space.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "空间不存在或无权限");
        //查询指定图片（进选择需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (CollUtil.isEmpty(pictureList)) {
            return;
        }
        //更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });
        //批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);
        //操作数据库进行批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量操作失败");
    }

    public void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (StrUtil.isBlank(nameRule) || CollUtil.isEmpty(pictureList)) {
            return;
        }
        long count = 1L;
        try {
            for (Picture picture : pictureList) {
                //替换规则
                String name = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(name);
            }
        }catch (Exception e) {
            log.error("名称解析失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析失败");
        }

    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        //校验图片是否存在
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        //校验权限
        checkPictureAuth(picture, loginUser);
        //创建扩图任务
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        //调用扩图接口
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
    }
}




