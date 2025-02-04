package com.dd.spring.yunpicturebackend;

import com.dd.spring.yunpicturebackend.manager.CosManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class YunPictureBackendApplicationTests {

    @Test
    void contextLoads() {
        CosManager cosManager = new CosManager();
        String imageAve = cosManager.getImageAve("https://yun-picture-1326934174.cos.ap-beijing.myqcloud.com//public/1883089374443782145/2025-01-30_dU1rzy6hIYcmziHS.Wn7AyjNNE2TmfwQH2AIb0AHaE7");
        System.out.println(imageAve);
    }

}
