package com.dd.spring.yunpicturebackend.model.vo.picture;

import com.dd.spring.yunpicturebackend.model.entity.Picture;
import lombok.Data;

import java.util.List;

/**
 * 图片标签分类
 */
@Data
public class PictureTagCategory {
    /**
     * 标签列表
     */
    private List<String> tagList;
    /**
     * 分类列表
     */
    private List<String> categoryList;
}
