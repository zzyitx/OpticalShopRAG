package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.model.UserPermissionOverride;
import com.yizhaoqi.smartpai.repository.RolePermissionRepository;
import com.yizhaoqi.smartpai.repository.UserBusinessRoleRepository;
import com.yizhaoqi.smartpai.repository.UserPermissionOverrideRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    @Mock
    private UserBusinessRoleRepository userBusinessRoleRepository;
    @Mock
    private RolePermissionRepository rolePermissionRepository;
    @Mock
    private UserPermissionOverrideRepository userPermissionOverrideRepository;

    private PermissionService permissionService;

    @BeforeEach
    void setUp() {
        permissionService = new PermissionService(
                userBusinessRoleRepository,
                rolePermissionRepository,
                userPermissionOverrideRepository
        );
    }

    @Test
    void adminAlwaysHasEveryPermission() {
        User admin = user(1L, User.Role.ADMIN);

        assertThat(permissionService.hasPermission(admin, "store.sales-bill.template.download")).isTrue();
        assertThat(permissionService.getEffectivePermissions(admin)).containsExactly(PermissionService.ALL_PERMISSIONS);
    }

    @Test
    void explicitDenyOverridesRoleAndUserGrant() {
        User user = user(2L, User.Role.USER);
        when(userBusinessRoleRepository.findRoleIdsByUserId(2L)).thenReturn(List.of(10L));
        when(rolePermissionRepository.findPermissionCodesByRoleIds(List.of(10L)))
                .thenReturn(List.of("rag.chat.use", "store.sales-bill.view"));
        when(userPermissionOverrideRepository.findByUserId(2L)).thenReturn(List.of(
                override("store.sales-bill.import", UserPermissionOverride.Effect.GRANT),
                override("store.sales-bill.view", UserPermissionOverride.Effect.DENY)
        ));

        Set<String> effective = permissionService.getEffectivePermissions(user);

        assertThat(effective).containsExactlyInAnyOrder("rag.chat.use", "store.sales-bill.import");
    }

    @Test
    void userWithoutMigratedRoleKeepsCurrentRagCapabilities() {
        User user = user(3L, User.Role.USER);
        when(userBusinessRoleRepository.findRoleIdsByUserId(3L)).thenReturn(List.of());
        when(userPermissionOverrideRepository.findByUserId(3L)).thenReturn(List.of());

        assertThat(permissionService.getEffectivePermissions(user))
                .containsExactlyInAnyOrder("rag.chat.use", "rag.knowledge-base.view", "rag.knowledge-base.upload");
    }

    private User user(Long id, User.Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private UserPermissionOverride override(String code, UserPermissionOverride.Effect effect) {
        UserPermissionOverride override = new UserPermissionOverride();
        override.setPermissionCode(code);
        override.setEffect(effect);
        return override;
    }
}
