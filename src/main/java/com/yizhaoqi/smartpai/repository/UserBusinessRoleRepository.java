package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.UserBusinessRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserBusinessRoleRepository extends JpaRepository<UserBusinessRole, Long> {

    List<UserBusinessRole> findByUserId(Long userId);

    void deleteByUserId(Long userId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    @Query("select ubr.roleId from UserBusinessRole ubr where ubr.userId = :userId")
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);
}
