package com.yizhaoqi.smartpai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@Table(name = "user_permission_overrides", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "permission_code"}))
/** 记录用户级额外授予或明确拒绝，明确拒绝优先于角色授权。 */
public class UserPermissionOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "permission_code", nullable = false, length = 120)
    private String permissionCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Effect effect;

    public enum Effect {
        GRANT,
        DENY
    }
}
