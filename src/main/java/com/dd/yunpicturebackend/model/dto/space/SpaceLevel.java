package com.dd.yunpicturebackend.model.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SpaceLevel {
    /**
     * 指
     */
    private int value;
    /**
     * 中文
     */
    private String text;
    /**
     * 最大数量
     */
    private long maxCount;
    /**
     * 最大大小
     */
    private long maxSize;
}
