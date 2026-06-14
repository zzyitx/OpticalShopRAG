package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.BusinessRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessRoleRepository extends JpaRepository<BusinessRole, Long> {
    Optional<BusinessRole> findByCode(String code);
}
