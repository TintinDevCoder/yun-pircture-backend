package com.dd.yunpicturebackend.api.imagesearch.sub;

import cn.hutool.core.util.URLUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONUtil;
import com.dd.yunpicturebackend.exception.BusinessException;
import com.dd.yunpicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 获取以图搜图页面地址(step 1)
 */
@Slf4j
public class GetImagePageUrlApi {
    /**
     * 获取以图搜图页面地址
     * @param imageUrl
     * @return
     */
    public static String getImagePageUrl(String imageUrl) {
        //1、准备请求参数
        Map<String, Object> formData = new HashMap<>();
        formData.put("image", imageUrl);
        formData.put("tn", "pc");
        formData.put("from", "pc");
        formData.put("image_source", "PC_UPLOAD_URL");
        //获取当前时间戳
        long currentTimeMillis = System.currentTimeMillis();
        //请求地址
        String url = "https://graph.baidu.com/upload?updatetime=" + currentTimeMillis;
        try{
            //2、发送请求
            HttpResponse httpResponse = HttpRequest.post(url)
                    .form(formData)
                    .timeout(5000)
                    .execute();
            if (httpResponse.getStatus() != HttpStatus.HTTP_OK) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            //3、解析响应
            String body = httpResponse.body();
            log.info("获取以图搜图页面地址接口返回结果:{}", body);
            Map<String, Object> resultMap = JSONUtil.toBean(body, Map.class);
            //处理接口返回结果
            if (resultMap == null || !Integer.valueOf(0).equals(resultMap.get("status"))) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "接口调用失败");
            }
            Map<String, Object> data = (Map<String, Object>) resultMap.get("data");
            String rawUrl = (String) data.get("url");
            //4、解码url
            String searchResultUrl = URLUtil.decode(rawUrl, StandardCharsets.UTF_8);
            //如果url为空，抛出异常
            if (searchResultUrl == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "未返回有效的结果地址");
            }
            return searchResultUrl;
        }catch (Exception e){
            log.error("获取以图搜图页面地址失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取以图搜图页面地址失败");
        }
    }

    public static void main(String[] args) {
        // 测试以图搜图功能
        String imageUrl = "https://www.codefather.cn/logo.png";
        String searchResultUrl = getImagePageUrl(imageUrl);
        System.out.println(searchResultUrl);
    }
}
