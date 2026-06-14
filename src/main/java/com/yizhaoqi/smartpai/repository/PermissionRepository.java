package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermissionRepository extends JpaRepository<Permission, String> {
}
