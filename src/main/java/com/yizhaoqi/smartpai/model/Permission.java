package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "permissions")
/** 定义跨眼镜店、RAG 和系统管理使用的稳定功能权限目录。 */
public class Permission {

    @Id
    @Column(length = 120)
    private String code;

    @Column(nullable = false, length = 40)
    private String systemCode;

    @Column(nullable = false, length = 60)
    private String moduleName;

    @Column(nullable = false, length = 40)
    private String operation;

    @Column(nullable = false, length = 200)
    private String description;
}
