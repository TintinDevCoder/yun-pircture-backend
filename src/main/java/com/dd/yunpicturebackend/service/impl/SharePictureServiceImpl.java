package com.dd.yunpicturebackend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dd.yunpicturebackend.model.entity.SharePicture;
import com.dd.yunpicturebackend.service.SharePictureService;
import com.dd.yunpicturebackend.mapper.SharePictureMapper;
import org.springframework.stereotype.Service;

/**
* @author DELL
* @description 针对表【share_picture(图片)】的数据库操作Service实现
* @createDate 2025-02-04 21:07:11
*/
@Service
public class SharePictureServiceImpl extends ServiceImpl<SharePictureMapper, SharePicture>
    implements SharePictureService{
}




