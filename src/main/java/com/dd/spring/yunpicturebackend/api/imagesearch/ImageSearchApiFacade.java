package com.dd.spring.yunpicturebackend.api.imagesearch;

import com.dd.spring.yunpicturebackend.api.imagesearch.model.ImageSearchResult;
import com.dd.spring.yunpicturebackend.api.imagesearch.sub.GetImageFirstUrlApi;
import com.dd.spring.yunpicturebackend.api.imagesearch.sub.GetImageListApi;
import com.dd.spring.yunpicturebackend.api.imagesearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class ImageSearchApiFacade {
    /**
     * 搜索图片
     * @param ImageUrl
     * @return
     */
    public static List<ImageSearchResult> searchImage(String ImageUrl) {
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(ImageUrl);
        String imageListUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageListUrl);
        return imageList;
    }

    public static void main(String[] args) {
        String imageUrl = "https://www.codefather.cn/logo.png";
        List<ImageSearchResult> imageList = searchImage(imageUrl);
        System.out.println(imageList);
    }
}
