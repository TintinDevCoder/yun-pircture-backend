package com.dd.spring.yunpicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dd.spring.yunpicturebackend.model.dto.picture.PictureQueryRequest;
import com.dd.spring.yunpicturebackend.model.dto.picture.PictureReviewRequest;
import com.dd.spring.yunpicturebackend.model.dto.picture.PictureUploadByBatchRequest;
import com.dd.spring.yunpicturebackend.model.dto.picture.PictureUploadRequest;
import com.dd.spring.yunpicturebackend.model.entity.Picture;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dd.spring.yunpicturebackend.model.entity.User;
import com.dd.spring.yunpicturebackend.model.vo.picture.PictureVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author DELL
* @description 针对表【picture(图片)】的数据库操作Service
* @createDate 2025-01-26 09:33:03
*/
public interface PictureService extends IService<Picture> {
    /**
     * 上传图片
     * @param inputSource 文件输入源
     * @param pictureUploadRequest
     * @param loginUser
     * @return
     */
    PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser);


    /**
     * 获取查询对象
     * @param pictureQueryRequest
     * @return
     */
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest);

    /**
     * 获取图片包装类（单条） 用户用、需要脱敏
     * @param picture
     * @param request
     * @return
     */
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request);

    /**
     * 获取图片包装类（分页） 用户用、需要脱敏
     * @param picturePage
     * @param request
     * @return
     */
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request);

    /**
     * 校验数据
     * @param picture
     */
    public void validPicture(Picture picture);



    /**
     * 图片审核
     * @param pictureReviewRequest
     * @param loginUser
     */
    void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser);

    /**
     * 填充审核参数
     * @param picture
     * @param loginUser
     */
    public void fillReviewParams(Picture picture, User loginUser);
    /**
     * 批量抓取和创建图片
     *
     * @param pictureUploadByBatchRequest
     * @param loginUser
     * @return 成功创建的图片数
     */
    Integer uploadPictureByBatch(
            PictureUploadByBatchRequest pictureUploadByBatchRequest,
            User loginUser
    );

}
