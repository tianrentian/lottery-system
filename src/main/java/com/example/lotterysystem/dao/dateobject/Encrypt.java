package com.example.lotterysystem.dao.dateobject;

import lombok.Data;

@Data
public class Encrypt {
    private String value;
    public Encrypt(byte[] decrypt) {} // 写不带参的构造是为了将来序列化每个Encrypt对象时必须带一个无参的构造，否则会失败
    public Encrypt(String value) {
        this.value = value;
    }
}
