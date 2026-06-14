import { request } from '../request';

export interface PermissionItem {
  code: string;
  systemCode: 'store' | 'rag' | 'system';
  moduleName: string;
  operation: string;
  description: string;
}

export interface BusinessRoleItem {
  id: number;
  code: string;
  name: string;
  active: boolean;
  systemRole: boolean;
  permissionCodes: string[];
}

export interface PermissionUserItem {
  id: number;
  username: string;
  role: 'USER' | 'ADMIN';
}

export interface UserAuthorization {
  userId: number;
  roleIds: number[];
  overrides: Array<{ permissionCode: string; effect: 'GRANT' | 'DENY' }>;
  effectivePermissions: string[];
  orgTags: string;
  primaryOrg: string;
}

export function fetchPermissionCatalog() {
  return request<PermissionItem[]>({ url: '/admin/permissions/catalog' });
}

export function fetchBusinessRoles() {
  return request<BusinessRoleItem[]>({ url: '/admin/permissions/roles' });
}

export function fetchCreateBusinessRole(data: { code: string; name: string; permissionCodes: string[] }) {
  return request({ url: '/admin/permissions/roles', method: 'post', data });
}

export function fetchUpdateBusinessRolePermissions(roleId: number, permissionCodes: string[]) {
  return request({ url: `/admin/permissions/roles/${roleId}/permissions`, method: 'put', data: { permissionCodes } });
}

export function fetchPermissionUsers() {
  return request<PermissionUserItem[]>({ url: '/admin/users' });
}

export function fetchUserAuthorization(userId: number) {
  return request<UserAuthorization>({ url: `/admin/permissions/users/${userId}` });
}

export function fetchUpdateUserAuthorization(
  userId: number,
  data: { roleIds: number[]; grants: string[]; denies: string[]; orgTags: string[]; primaryOrg: string }
) {
  return request<UserAuthorization>({ url: `/admin/permissions/users/${userId}`, method: 'put', data });
}
