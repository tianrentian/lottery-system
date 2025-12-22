package com.example.lotterysystem;

import com.example.lotterysystem.common.utils.SMSUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class SMSTest {
    @Autowired
    private SMSUtil smsUtil;

    @Test
    void smsTest() throws Exception {
        smsUtil.sendMessage(
                "100001",
                "15103838550",
                "{\"code\":\"##code##\",\"min\":\"5\"}");
    }

}
