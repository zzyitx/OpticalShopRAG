package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    List<RolePermission> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);

    @Query("select distinct rp.permissionCode from RolePermission rp where rp.roleId in :roleIds")
    List<String> findPermissionCodesByRoleIds(@Param("roleIds") Collection<Long> roleIds);
}
