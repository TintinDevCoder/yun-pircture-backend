package com.dd.spring.yunpicturebackend.schedule;

import com.dd.spring.yunpicturebackend.config.CosClientConfig;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.DeleteObjectsRequest;
import com.qcloud.cos.model.ListObjectsRequest;
import com.qcloud.cos.model.ObjectListing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Component
@EnableScheduling
public class CosCleanupTask {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    @Scheduled(fixedRate = 86400000) // 每24小时执行一次
    public void deleteOldImages() {
        String bucketName = cosClientConfig.getBucket();
        List<String> keysToDelete = new ArrayList<>();
        // 列出存储桶中的所有对象
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName);
        ObjectListing objectListing = cosClient.listObjects(listObjectsRequest);

        // 遍历对象，筛选需要删除的图片
        for (COSObjectSummary objectSummary : objectListing.getObjectSummaries()) {
            String key = objectSummary.getKey();
            if (!key.endsWith(".webp") && !key.contains("thumbnail")) {
                keysToDelete.add(key);
            }
        }

        // 删除不符合条件的图片
        if (!keysToDelete.isEmpty()) {
            DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketName).withKeys(keysToDelete.toArray(new String[0]));
            cosClient.deleteObjects(deleteObjectsRequest);
        }
    }
}