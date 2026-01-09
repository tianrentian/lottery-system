package com.example.lotterysystem;

import com.example.lotterysystem.common.utils.MailUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class MailTest {

    @Autowired
    private MailUtil mailUtil;

    @Test
    void sendMessage() {
        mailUtil.sendSampleMail("2532431590@qq.com", "标题", "正文");
    }

}