package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.BusinessRole;
import com.yizhaoqi.smartpai.model.RolePermission;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.model.UserBusinessRole;
import com.yizhaoqi.smartpai.model.UserPermissionOverride;
import com.yizhaoqi.smartpai.repository.BusinessRoleRepository;
import com.yizhaoqi.smartpai.repository.PermissionRepository;
import com.yizhaoqi.smartpai.repository.RolePermissionRepository;
import com.yizhaoqi.smartpai.repository.UserBusinessRoleRepository;
import com.yizhaoqi.smartpai.repository.UserPermissionOverrideRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.service.PermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/permissions")
public class PermissionAdminController {

    private final PermissionRepository permissionRepository;
    private final BusinessRoleRepository businessRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserBusinessRoleRepository userBusinessRoleRepository;
    private final UserPermissionOverrideRepository userPermissionOverrideRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    public PermissionAdminController(PermissionRepository permissionRepository,
                                     BusinessRoleRepository businessRoleRepository,
                                     RolePermissionRepository rolePermissionRepository,
                                     UserBusinessRoleRepository userBusinessRoleRepository,
                                     UserPermissionOverrideRepository userPermissionOverrideRepository,
                                     UserRepository userRepository,
                                     PermissionService permissionService) {
        this.permissionRepository = permissionRepository;
        this.businessRoleRepository = businessRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userBusinessRoleRepository = userBusinessRoleRepository;
        this.userPermissionOverrideRepository = userPermissionOverrideRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
    }

    @GetMapping("/catalog")
    public ResponseEntity<?> catalog() {
        return success(permissionRepository.findAll());
    }

    @GetMapping("/roles")
    public ResponseEntity<?> roles() {
        return success(businessRoleRepository.findAll().stream().map(role -> Map.of(
                "id", role.getId(),
                "code", role.getCode(),
                "name", role.getName(),
                "active", role.isActive(),
                "systemRole", role.isSystemRole(),
                "permissionCodes", rolePermissionRepository.findByRoleId(role.getId()).stream()
                        .map(RolePermission::getPermissionCode).toList()
        )).toList());
    }

    @PostMapping("/roles")
    @Transactional
    public ResponseEntity<?> createRole(@RequestBody RoleRequest request) {
        if (businessRoleRepository.findByCode(request.code()).isPresent()) {
            throw new CustomException("PERMISSION_ROLE_CODE_EXISTS", HttpStatus.CONFLICT);
        }
        BusinessRole role = new BusinessRole();
        role.setCode(request.code());
        role.setName(request.name());
        role.setActive(true);
        role = businessRoleRepository.save(role);
        replaceRolePermissions(role.getId(), request.permissionCodes());
        return success(role);
    }

    @PutMapping("/roles/{roleId}/permissions")
    @Transactional
    public ResponseEntity<?> updateRolePermissions(@PathVariable Long roleId, @RequestBody PermissionCodesRequest request) {
        BusinessRole role = businessRoleRepository.findById(roleId)
                .orElseThrow(() -> new CustomException("PERMISSION_ROLE_NOT_FOUND", HttpStatus.NOT_FOUND));
        if (role.isSystemRole()) {
            throw new CustomException("SYSTEM_PERMISSION_ROLE_IS_IMMUTABLE", HttpStatus.BAD_REQUEST);
        }
        replaceRolePermissions(roleId, request.permissionCodes());
        return success(Map.of("roleId", roleId));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<?> userAuthorization(@PathVariable Long userId) {
        User user = findUser(userId);
        return success(Map.of(
                "userId", userId,
                "roleIds", userBusinessRoleRepository.findRoleIdsByUserId(userId),
                "overrides", userPermissionOverrideRepository.findByUserId(userId),
                "effectivePermissions", permissionService.getEffectivePermissions(user),
                "orgTags", user.getOrgTags() == null ? "" : user.getOrgTags(),
                "primaryOrg", user.getPrimaryOrg() == null ? "" : user.getPrimaryOrg()
        ));
    }

    @PutMapping("/users/{userId}")
    @Transactional
    public ResponseEntity<?> updateUserAuthorization(@PathVariable Long userId, @RequestBody UserAuthorizationRequest request) {
        User user = findUser(userId);
        if (user.getRole() == User.Role.ADMIN) {
            throw new CustomException("ADMIN_PERMISSIONS_ARE_IMMUTABLE", HttpStatus.BAD_REQUEST);
        }

        userBusinessRoleRepository.deleteByUserId(userId);
        request.roleIds().forEach(roleId -> {
            if (!businessRoleRepository.existsById(roleId)) {
                throw new CustomException("PERMISSION_ROLE_NOT_FOUND", HttpStatus.BAD_REQUEST);
            }
            UserBusinessRole assignment = new UserBusinessRole();
            assignment.setUserId(userId);
            assignment.setRoleId(roleId);
            userBusinessRoleRepository.save(assignment);
        });

        userPermissionOverrideRepository.deleteByUserId(userId);
        Set<String> grants = new java.util.LinkedHashSet<>(request.grants());
        grants.removeAll(request.denies());
        saveOverrides(userId, grants, UserPermissionOverride.Effect.GRANT);
        saveOverrides(userId, request.denies(), UserPermissionOverride.Effect.DENY);
        user.setOrgTags(String.join(",", request.orgTags()));
        user.setPrimaryOrg(request.primaryOrg());
        userRepository.save(user);
        return userAuthorization(userId);
    }

    private void replaceRolePermissions(Long roleId, Set<String> permissionCodes) {
        rolePermissionRepository.deleteByRoleId(roleId);
        permissionCodes.forEach(code -> {
            if (!permissionRepository.existsById(code)) {
                throw new CustomException("PERMISSION_CODE_NOT_FOUND", HttpStatus.BAD_REQUEST);
            }
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(roleId);
            rolePermission.setPermissionCode(code);
            rolePermissionRepository.save(rolePermission);
        });
    }

    private void saveOverrides(Long userId, Set<String> permissionCodes, UserPermissionOverride.Effect effect) {
        permissionCodes.forEach(code -> {
            if (!permissionRepository.existsById(code)) {
                throw new CustomException("PERMISSION_CODE_NOT_FOUND", HttpStatus.BAD_REQUEST);
            }
            UserPermissionOverride override = new UserPermissionOverride();
            override.setUserId(userId);
            override.setPermissionCode(code);
            override.setEffect(effect);
            userPermissionOverrideRepository.save(override);
        });
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("USER_NOT_FOUND", HttpStatus.NOT_FOUND));
    }

    private ResponseEntity<?> success(Object data) {
        return ResponseEntity.ok(Map.of("code", 200, "message", "success", "data", data));
    }

    public record RoleRequest(String code, String name, Set<String> permissionCodes) {
    }

    public record PermissionCodesRequest(Set<String> permissionCodes) {
    }

    public record UserAuthorizationRequest(List<Long> roleIds,
                                           Set<String> grants,
                                           Set<String> denies,
                                           List<String> orgTags,
                                           String primaryOrg) {
    }
}
