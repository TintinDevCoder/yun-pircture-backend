package com.dd.yunpicturebackend.manager;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.dd.yunpicturebackend.config.CosClientConfig;
import com.dd.yunpicturebackend.exception.BusinessException;
import com.dd.yunpicturebackend.exception.ErrorCode;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


@Component
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        return cosClient.putObject(putObjectRequest);
    }
    /**
     * 下载对象
     *
     * @param key 唯一键
     */
    public COSObject getObject(String key) {
        GetObjectRequest getObjectRequest = new GetObjectRequest(cosClientConfig.getBucket(), key);
        return cosClient.getObject(getObjectRequest);
    }

    /**
     * 上传对象（附带图片信息）
     *
     * @param key  唯一键
     * @param file 文件
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        //图片处理规则
        List<PicOperations.Rule> rules = new ArrayList<>();
        //图片压缩（转为webp格式）
        String webpKey = FileUtil.mainName(key) + ".webp";
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setFileId(webpKey);
        compressRule.setRule("imageMogr2/format/webp");
        compressRule.setBucket(cosClientConfig.getBucket());
        rules.add(compressRule);
        //略缩图处理,进队 > 20kB的图片生成缩略图
        if (file.length() > 2 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            //拼接缩略图的路径
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
            thumbnailRule.setFileId(thumbnailKey);
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s", 256, 256));
            thumbnailRule.setBucket(cosClientConfig.getBucket());
            rules.add(thumbnailRule);
        }
        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }
    /**
     * 删除对象
     *
     * @param url  唯一键
     */
    public void deleteObject(String url) {
        String key = url.replace(cosClientConfig.getHost(), "");
        // 执行删除操作
        cosClient.deleteObject(cosClientConfig.getBucket(), key);
    }
    /**
     * 获取图片主色调
     *
     * @param key 文件 key
     * @return 图片主色调
     */
    public String getImageAve(String key) {
        GetObjectRequest getObj = new GetObjectRequest(cosClientConfig.getBucket(), key);
        String rule = "imageAve";
        getObj.putCustomQueryParameter(rule, null);
        COSObject object = cosClient.getObject(getObj);
        COSObjectInputStream objectContent = object.getObjectContent();
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            CloseableHttpResponse httpResponse = httpClient.execute(objectContent.getHttpRequest());
            String response = EntityUtils.toString(httpResponse.getEntity());
            return JSONUtil.parseObj(response).getStr("RGB");
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }
}
