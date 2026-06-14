<script setup lang="ts">
import {
  fetchBusinessRoles,
  fetchCreateBusinessRole,
  fetchPermissionCatalog,
  fetchPermissionUsers,
  fetchUpdateBusinessRolePermissions,
  fetchUpdateUserAuthorization,
  fetchUserAuthorization
} from '@/service/api';
import type { BusinessRoleItem, PermissionItem, PermissionUserItem } from '@/service/api';

const loading = ref(false);
const catalog = ref<PermissionItem[]>([]);
const roles = ref<BusinessRoleItem[]>([]);
const users = ref<PermissionUserItem[]>([]);
const selectedRoleId = ref<number | null>(null);
const selectedUserId = ref<number | null>(null);
const rolePermissionCodes = ref<string[]>([]);
const roleForm = reactive({ code: '', name: '', permissionCodes: [] as string[] });
const userForm = reactive({
  roleIds: [] as number[],
  grants: [] as string[],
  denies: [] as string[],
  orgTagsText: '',
  primaryOrg: ''
});

const permissionOptions = computed(() =>
  catalog.value.map(item => ({
    label: `[${item.systemCode}] ${item.moduleName} / ${item.description}`,
    value: item.code
  }))
);
const roleOptions = computed(() => roles.value.map(item => ({ label: item.name, value: item.id })));
const userOptions = computed(() => users.value.map(item => ({ label: `${item.username} (${item.role})`, value: item.id })));
const selectedRole = computed(() => roles.value.find(item => item.id === selectedRoleId.value));
const catalogColumns = [
  { title: '权限码', key: 'code', minWidth: 250 },
  { title: '所属系统', key: 'systemCode', width: 120 },
  { title: '功能模块', key: 'moduleName', width: 160 },
  { title: '操作', key: 'operation', width: 120 },
  { title: '说明', key: 'description', minWidth: 180 }
];

async function loadData() {
  loading.value = true;
  try {
    const [catalogRes, rolesRes, usersRes] = await Promise.all([
      fetchPermissionCatalog(),
      fetchBusinessRoles(),
      fetchPermissionUsers()
    ]);
    if (!catalogRes.error && catalogRes.data) catalog.value = catalogRes.data;
    if (!rolesRes.error && rolesRes.data) roles.value = rolesRes.data;
    if (!usersRes.error && usersRes.data) users.value = usersRes.data;
  } finally {
    loading.value = false;
  }
}

function selectRole(roleId: number | null) {
  selectedRoleId.value = roleId;
  rolePermissionCodes.value = roles.value.find(item => item.id === roleId)?.permissionCodes || [];
}

async function createRole() {
  if (!roleForm.code || !roleForm.name) {
    window.$message?.warning('请填写角色编码和名称');
    return;
  }
  const { error } = await fetchCreateBusinessRole(roleForm);
  if (!error) {
    window.$message?.success('角色已创建');
    Object.assign(roleForm, { code: '', name: '', permissionCodes: [] });
    await loadData();
  }
}

async function saveRolePermissions() {
  if (!selectedRoleId.value) return;
  const { error } = await fetchUpdateBusinessRolePermissions(selectedRoleId.value, rolePermissionCodes.value);
  if (!error) {
    window.$message?.success('角色权限已保存');
    await loadData();
    selectRole(selectedRoleId.value);
  }
}

async function selectUser(userId: number | null) {
  selectedUserId.value = userId;
  if (!userId) return;
  const { error, data } = await fetchUserAuthorization(userId);
  if (!error && data) {
    userForm.roleIds = data.roleIds;
    userForm.grants = data.overrides.filter(item => item.effect === 'GRANT').map(item => item.permissionCode);
    userForm.denies = data.overrides.filter(item => item.effect === 'DENY').map(item => item.permissionCode);
    userForm.orgTagsText = data.orgTags;
    userForm.primaryOrg = data.primaryOrg;
  }
}

async function saveUserAuthorization() {
  if (!selectedUserId.value) return;
  const { error } = await fetchUpdateUserAuthorization(selectedUserId.value, {
    roleIds: userForm.roleIds,
    grants: userForm.grants,
    denies: userForm.denies,
    orgTags: userForm.orgTagsText.split(',').map(item => item.trim()).filter(Boolean),
    primaryOrg: userForm.primaryOrg
  });
  if (!error) window.$message?.success('用户权限与数据范围已保存');
}

onMounted(loadData);
</script>

<template>
  <div class="min-h-500px flex-col-stretch gap-16px overflow-auto">
    <NCard title="权限中心" :bordered="false" size="small" class="card-wrapper">
      <template #header-extra>
        <NButton type="primary" :loading="loading" @click="loadData">刷新</NButton>
      </template>
      <NAlert type="info" :bordered="false">
        功能权限决定用户能做什么，组织标签决定用户可以访问哪些 RAG 数据。ADMIN 永久拥有全部权限且不可降权。
      </NAlert>
    </NCard>

    <NCard :bordered="false" size="small" class="card-wrapper">
      <NTabs type="line" animated>
        <NTabPane name="roles" tab="角色管理">
          <NGrid responsive="screen" cols="1 l:2" :x-gap="16" :y-gap="16">
            <NGi>
              <NCard title="新建角色" size="small">
                <NForm label-placement="left" :label-width="90">
                  <NFormItem label="角色编码"><NInput v-model:value="roleForm.code" placeholder="STORE_MANAGER" /></NFormItem>
                  <NFormItem label="角色名称"><NInput v-model:value="roleForm.name" placeholder="店长" /></NFormItem>
                  <NFormItem label="角色权限">
                    <NSelect v-model:value="roleForm.permissionCodes" multiple filterable :options="permissionOptions" />
                  </NFormItem>
                  <NButton type="primary" @click="createRole">创建角色</NButton>
                </NForm>
              </NCard>
            </NGi>
            <NGi>
              <NCard title="配置角色权限" size="small">
                <NForm label-placement="left" :label-width="90">
                  <NFormItem label="选择角色"><NSelect :value="selectedRoleId" :options="roleOptions" @update:value="selectRole" /></NFormItem>
                  <NFormItem label="功能权限">
                    <NSelect v-model:value="rolePermissionCodes" multiple filterable :options="permissionOptions" />
                  </NFormItem>
                  <NButton type="primary" :disabled="!selectedRole || selectedRole.systemRole" @click="saveRolePermissions">
                    保存角色权限
                  </NButton>
                  <span v-if="selectedRole?.systemRole" class="ml-12px text-12px text-gray">系统保留角色不可修改</span>
                </NForm>
              </NCard>
            </NGi>
          </NGrid>
        </NTabPane>

        <NTabPane name="users" tab="用户授权">
          <NForm label-placement="left" :label-width="110" class="max-w-1000px">
            <NFormItem label="选择用户"><NSelect :value="selectedUserId" filterable :options="userOptions" @update:value="selectUser" /></NFormItem>
            <NFormItem label="所属角色"><NSelect v-model:value="userForm.roleIds" multiple :options="roleOptions" /></NFormItem>
            <NFormItem label="额外授予"><NSelect v-model:value="userForm.grants" multiple filterable :options="permissionOptions" /></NFormItem>
            <NFormItem label="明确拒绝"><NSelect v-model:value="userForm.denies" multiple filterable :options="permissionOptions" /></NFormItem>
            <NFormItem label="组织标签"><NInput v-model:value="userForm.orgTagsText" placeholder="多个标签使用英文逗号分隔" /></NFormItem>
            <NFormItem label="主组织标签"><NInput v-model:value="userForm.primaryOrg" /></NFormItem>
            <NAlert type="warning" :bordered="false" class="mb-16px">明确拒绝优先于角色权限和额外授予。</NAlert>
            <NButton type="primary" :disabled="!selectedUserId" @click="saveUserAuthorization">保存用户授权</NButton>
          </NForm>
        </NTabPane>

        <NTabPane name="catalog" tab="权限目录">
          <NDataTable :columns="catalogColumns" :data="catalog" :loading="loading" :scroll-x="900" />
        </NTabPane>
      </NTabs>
    </NCard>
  </div>
</template>

<style scoped lang="scss"></style>
