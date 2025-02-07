package com.dd.yunpicturebackend;

import com.dd.yunpicturebackend.manager.CosManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@SpringBootTest
class YunPictureBackendApplicationTests {

    @Test
    void contextLoads() {
        Map<String, String> map = new HashMap<>();
        Iterator<Map.Entry<String, String>> iterator = map.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            System.out.println(entry.getKey() + " " + entry.getValue());
        }

    }

}
