package com.yizhaoqi.smartpai.security;

import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.service.PermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("permissionAuthorization")
public class PermissionAuthorization {

    private final UserRepository userRepository;
    private final PermissionService permissionService;

    public PermissionAuthorization(UserRepository userRepository, PermissionService permissionService) {
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    public boolean has(Authentication authentication, String permissionCode) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        return user != null && permissionService.hasPermission(user, permissionCode);
    }
}
