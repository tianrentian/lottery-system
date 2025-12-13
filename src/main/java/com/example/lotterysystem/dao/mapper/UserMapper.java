package com.example.lotterysystem.dao.mapper;

import com.example.lotterysystem.dao.dateobject.Encrypt;
import com.example.lotterysystem.dao.dateobject.UserDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    /**
     * 查询邮箱绑定的人数
     * @param email
     * @return
     */
    @Select("select count(*) from user where email = #{email}")
    int countByMail(@Param("email") String email);

    @Select("select count(*) from user where phone_number = #{phoneNumber}")
    int countByPhone(@Param("phoneNumber") Encrypt phoneNumber);

    // 换行记得加空格" "
    @Insert("insert into user (user_name, email, phone_number, password, identity)" +
            " values(#{userName}, #{email}, #{phoneNumber}, #{password}, #{identity})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    void insert(UserDO userDO);
}
