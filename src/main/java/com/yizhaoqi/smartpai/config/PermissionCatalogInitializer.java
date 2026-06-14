package com.yizhaoqi.smartpai.config;

import com.yizhaoqi.smartpai.model.BusinessRole;
import com.yizhaoqi.smartpai.model.Permission;
import com.yizhaoqi.smartpai.model.RolePermission;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.model.UserBusinessRole;
import com.yizhaoqi.smartpai.repository.BusinessRoleRepository;
import com.yizhaoqi.smartpai.repository.PermissionRepository;
import com.yizhaoqi.smartpai.repository.RolePermissionRepository;
import com.yizhaoqi.smartpai.repository.UserBusinessRoleRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(3)
public class PermissionCatalogInitializer implements CommandLineRunner {

    public static final String DEFAULT_RAG_ROLE = "DEFAULT_RAG_USER";

    private final PermissionRepository permissionRepository;
    private final BusinessRoleRepository businessRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserBusinessRoleRepository userBusinessRoleRepository;
    private final UserRepository userRepository;

    public PermissionCatalogInitializer(PermissionRepository permissionRepository,
                                        BusinessRoleRepository businessRoleRepository,
                                        RolePermissionRepository rolePermissionRepository,
                                        UserBusinessRoleRepository userBusinessRoleRepository,
                                        UserRepository userRepository) {
        this.permissionRepository = permissionRepository;
        this.businessRoleRepository = businessRoleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.userBusinessRoleRepository = userBusinessRoleRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<PermissionSeed> catalog = List.of(
                seed("store.dashboard.view", "store", "经营看板", "view", "查看经营看板"),
                seed("store.product.view", "store", "商品档案", "view", "查看商品档案"),
                seed("store.product.create", "store", "商品档案", "create", "新增商品"),
                seed("store.product.update", "store", "商品档案", "update", "编辑商品"),
                seed("store.inventory.view", "store", "库存管理", "view", "查看库存及流水"),
                seed("store.inventory.inbound.create", "store", "库存管理", "create", "创建和处理入库单"),
                seed("store.inventory.outbound.create", "store", "库存管理", "create", "创建和处理出库单"),
                seed("store.sales-bill.view", "store", "销售账单", "view", "查看账单和客户历史"),
                seed("store.sales-bill.create", "store", "销售账单", "create", "新增销售账单"),
                seed("store.sales-bill.update", "store", "销售账单", "update", "编辑销售账单"),
                seed("store.sales-bill.import", "store", "销售账单", "import", "导入销售账单"),
                seed("store.sales-bill.template.download", "store", "销售账单", "download", "下载销售账单模板"),
                seed("rag.chat.use", "rag", "智能问答", "use", "使用智能问答"),
                seed("rag.knowledge-base.view", "rag", "知识库", "view", "查看可访问知识库"),
                seed("rag.knowledge-base.upload", "rag", "知识库", "upload", "上传知识库文档"),
                seed("rag.knowledge-base.manage", "rag", "知识库", "manage", "管理知识库文档"),
                seed("rag.model-provider.manage", "rag", "模型配置", "manage", "管理模型配置"),
                seed("system.user.manage", "system", "用户管理", "manage", "管理用户"),
                seed("system.permission.manage", "system", "权限中心", "manage", "管理角色和权限")
        );
        catalog.forEach(this::savePermission);

        BusinessRole defaultRole = businessRoleRepository.findByCode(DEFAULT_RAG_ROLE).orElseGet(() -> {
            BusinessRole role = new BusinessRole();
            role.setCode(DEFAULT_RAG_ROLE);
            role.setName("默认 RAG 用户");
            role.setActive(true);
            role.setSystemRole(true);
            return businessRoleRepository.save(role);
        });

        List<String> defaultPermissions = List.of("rag.chat.use", "rag.knowledge-base.view", "rag.knowledge-base.upload");
        for (String code : defaultPermissions) {
            boolean exists = rolePermissionRepository.findByRoleId(defaultRole.getId()).stream()
                    .anyMatch(item -> code.equals(item.getPermissionCode()));
            if (!exists) {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(defaultRole.getId());
                rolePermission.setPermissionCode(code);
                rolePermissionRepository.save(rolePermission);
            }
        }

        userRepository.findAll().stream()
                .filter(user -> user.getRole() == User.Role.USER)
                .filter(user -> !userBusinessRoleRepository.existsByUserIdAndRoleId(user.getId(), defaultRole.getId()))
                .forEach(user -> {
                    UserBusinessRole assignment = new UserBusinessRole();
                    assignment.setUserId(user.getId());
                    assignment.setRoleId(defaultRole.getId());
                    userBusinessRoleRepository.save(assignment);
                });
    }

    private void savePermission(PermissionSeed seed) {
        Permission permission = permissionRepository.findById(seed.code()).orElseGet(Permission::new);
        permission.setCode(seed.code());
        permission.setSystemCode(seed.systemCode());
        permission.setModuleName(seed.moduleName());
        permission.setOperation(seed.operation());
        permission.setDescription(seed.description());
        permissionRepository.save(permission);
    }

    private PermissionSeed seed(String code, String systemCode, String moduleName, String operation, String description) {
        return new PermissionSeed(code, systemCode, moduleName, operation, description);
    }

    private record PermissionSeed(String code, String systemCode, String moduleName, String operation, String description) {
    }
}
