package com.example.lotterysystem.common.utils;


import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeResponse;
import com.aliyun.teautil.models.RuntimeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.aliyun.tea.*;

@Component
public class SMSUtil {
    private static final Logger logger = LoggerFactory.getLogger(SMSUtil.class);

     /**
     * 发送短信
     *
     * @param templateCode 模板号
     * @param phoneNumber 手机号
     * @param templateParam 模板参数
     */

    public void sendMessage(String templateCode, String phoneNumber, String templateParam) throws Exception {
        try {
            Client client = createClient();
            SendSmsVerifyCodeRequest sendSmsVerifyCodeRequest = new SendSmsVerifyCodeRequest()
                    .setSignName("速通互联验证码")
                    .setTemplateCode(templateCode)
                    .setPhoneNumber(phoneNumber)
                    .setTemplateParam(templateParam);
            RuntimeOptions runtime = new RuntimeOptions();
            SendSmsVerifyCodeResponse response = client.sendSmsVerifyCodeWithOptions(sendSmsVerifyCodeRequest, runtime);
            if (null != response.getBody()
                    && null != response.getBody().getMessage()
                    && "OK".equals(response.getBody().getMessage())) {
                logger.info("向{}发送信息成功，templateCode={}", phoneNumber, templateCode);
                return;
            }
            logger.error("向{}发送信息失败，templateCode={}，失败原因：{}",
                    phoneNumber, templateCode, response.getBody().getMessage());
        } catch (TeaException error) {
            logger.error("向{}发送信息失败，templateCode={}", phoneNumber, templateCode, error);
        } catch (Exception _error) {
            TeaException error = new TeaException(_error.getMessage(), _error);
            logger.error("向{}发送信息失败，templateCode={}", phoneNumber, templateCode, error);
        }
    }

    /**
     * <b>description</b> :
     * <p>使用凭据初始化账号Client</p>
     * @return Client
     *
     * @throws Exception
     */
    public static com.aliyun.dypnsapi20170525.Client createClient() throws Exception {
        // 工程代码建议使用更安全的无AK方式，凭据配置方式请参见：https://help.aliyun.com/document_detail/378657.html。
        com.aliyun.credentials.Client credential = new com.aliyun.credentials.Client();
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config()
                .setCredential(credential);
        // Endpoint 请参考 https://api.aliyun.com/product/Dypnsapi
        config.endpoint = "dypnsapi.aliyuncs.com";
        return new com.aliyun.dypnsapi20170525.Client(config);
    }

}