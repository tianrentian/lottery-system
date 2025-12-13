package com.example.lotterysystem.service;

import com.example.lotterysystem.controller.param.UserRegisterParam;
import com.example.lotterysystem.service.dto.UserRegisterDTO;

public interface UserService {

    /**
     * 注册
     * @param param
     * @return
     */
    UserRegisterDTO register(UserRegisterParam param);
}
