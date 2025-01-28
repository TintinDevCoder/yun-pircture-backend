package com.dd.spring.yunpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.dd.spring.yunpicturebackend.constant.FileConstant;
import com.dd.spring.yunpicturebackend.constant.error.FileErrorConstant;
import com.dd.spring.yunpicturebackend.exception.ErrorCode;
import com.dd.spring.yunpicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;
@Component
public class FilePictureUploadImpl extends PictureUploadTemplate {
    /**
     * 校验文件
     * @param inputSource
     */
    @Override
    protected void validPicture(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        ThrowUtils.throwIf(multipartFile == null, ErrorCode.PARAMS_ERROR, FileErrorConstant.FILE_EMPTY);
        // 1. 校验文件大小
        long fileSize = multipartFile.getSize();
        //FileConstant.ONE_M为1M大小
        ThrowUtils.throwIf(fileSize > FileConstant.FILE_MAX_SIZE, ErrorCode.PARAMS_ERROR, FileErrorConstant.FILE_EXCESSIVE);
        // 2. 校验文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        // 允许上传的文件后缀
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpeg", "jpg", "png", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(fileSuffix), ErrorCode.PARAMS_ERROR, FileErrorConstant.FILE_TYPE_ERROR);

    }
    /**
     * 获取图片名称
     * @param inputSource
     * @return
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        return multipartFile.getOriginalFilename();
    }

    /**
     * 文件下载
     * @param inputSource
     * @param file
     * @throws Exception
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputSource;
        multipartFile.transferTo(file);
    }
}
