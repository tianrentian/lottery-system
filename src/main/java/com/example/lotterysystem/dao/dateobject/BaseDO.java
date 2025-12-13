package com.example.lotterysystem.dao.dateobject;

import lombok.Data;

import java.io.Serializable;

@Data
public class BaseDO implements Serializable {
    /**
     * 主键
     */
    private Long id;

    /**
     * 创建时间
     */
    private Data gmtCreate;

    /**
     * 修改时间
     */
    private Data getModified;


}
