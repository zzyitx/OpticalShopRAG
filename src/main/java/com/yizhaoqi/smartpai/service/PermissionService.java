package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.model.UserPermissionOverride;
import com.yizhaoqi.smartpai.repository.RolePermissionRepository;
import com.yizhaoqi.smartpai.repository.UserBusinessRoleRepository;
import com.yizhaoqi.smartpai.repository.UserPermissionOverrideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PermissionService {

    public static final String ALL_PERMISSIONS = "*";
    public static final Set<String> DEFAULT_USER_PERMISSIONS = Set.of(
            "rag.chat.use",
            "rag.knowledge-base.view",
            "rag.knowledge-base.upload"
    );

    private final UserBusinessRoleRepository userBusinessRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;

    public PermissionService(UserBusinessRoleRepository userBusinessRoleRepository,
                             RolePermissionRepository rolePermissionRepository,
                             UserPermissionOverrideRepository userPermissionOverrideRepository) {
        this.userBusinessRoleRepository = userBusinessRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
    }

    @Transactional(readOnly = true)
    public Set<String> getEffectivePermissions(User user) {
        if (user.getRole() == User.Role.ADMIN) {
            return Set.of(ALL_PERMISSIONS);
        }

        Set<String> effective = new LinkedHashSet<>();
        List<Long> roleIds = userBusinessRoleRepository.findRoleIdsByUserId(user.getId());
        if (roleIds.isEmpty()) {
            effective.addAll(DEFAULT_USER_PERMISSIONS);
        } else {
            effective.addAll(rolePermissionRepository.findPermissionCodesByRoleIds(roleIds));
        }

        Set<String> denied = new LinkedHashSet<>();
        for (UserPermissionOverride override : userPermissionOverrideRepository.findByUserId(user.getId())) {
            if (override.getEffect() == UserPermissionOverride.Effect.DENY) {
                denied.add(override.getPermissionCode());
            } else {
                effective.add(override.getPermissionCode());
            }
        }
        effective.removeAll(denied);
        return Set.copyOf(effective);
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(User user, String permissionCode) {
        return user.getRole() == User.Role.ADMIN || getEffectivePermissions(user).contains(permissionCode);
    }
}
