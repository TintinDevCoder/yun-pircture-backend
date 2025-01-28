package com.dd.spring.yunpicturebackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.dd.spring.yunpicturebackend.constant.FileConstant;
import com.dd.spring.yunpicturebackend.constant.error.FileErrorConstant;
import com.dd.spring.yunpicturebackend.exception.BusinessException;
import com.dd.spring.yunpicturebackend.exception.ErrorCode;
import com.dd.spring.yunpicturebackend.exception.ThrowUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
@Component
public class UrlPictureUploadImpl extends PictureUploadTemplate {
    /**
     * 根据 url 校验文件
     * @param inputSource
     */
    @Override
    protected void validPicture(Object inputSource) {
        String fileUrl = (String) inputSource;
        //校验非空
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址为空");
        //校验 URL 格式
        try{
            new URL(fileUrl);
        }catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }
        //校验 URL 协议
        ThrowUtils.throwIf(!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://"),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 和 HTTPS 协议的文件地址");
        //发送 HEAD 请求验证文件是否存在
        HttpResponse httpResponse = null;

        try {
            httpResponse = HttpUtil.createRequest(Method.GET, fileUrl).execute();
            //未正常返回，无需执行其他判断
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            //文件存在，文件类型和大小校验
            String header = httpResponse.header("Content-Type");
            //不为空，才校验文件类型
            if (StrUtil.isNotBlank(header)) {
                // 允许的图片类型
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(header.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            //不为空，才校验文件大小
            String contentLengthStr = httpResponse.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long TWO_MB = FileConstant.FILE_MAX_SIZE; // 限制文件大小为 2MB
                    ThrowUtils.throwIf(contentLength > TWO_MB, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2M");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式错误");
                }
            }
        }finally {
            //释放资源
            if (httpResponse != null) {
                httpResponse.close();
            }
        }
    }

    /**
     * 根据 url 获取图片名称
     * @param inputSource
     * @return
     */
    @Override
    protected String getOriginFilename(Object inputSource) {
        String fileUrl = (String) inputSource;
        FileUtil.mainName(fileUrl);
        return fileUrl;
    }
    /**
     * 根据 url 下载文件
     * @param inputSource
     * @param file
     * @throws Exception
     */
    @Override
    protected void processFile(Object inputSource, File file) throws Exception {
        String fileUrl = (String) inputSource;
        HttpUtil.downloadFile(fileUrl, file);
    }
}
