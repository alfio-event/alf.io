import {fetchJson, postJson, putJson, callDelete} from '../service/helpers.ts';
import {User, Role, UserModification, BulkImportPayload} from '../model/user.ts';
import {Organization} from '../model/organization.ts';
import {ValidatedResponse} from '../model/validation.ts';

export interface UserValidationResult extends Omit<ValidatedResponse<UserModification>, "value"> {
}

export class UsersService {
    static loadAll(): Promise<User[]> {
        return fetchJson('/admin/api/users');
    }

    static load(id: number): Promise<User> {
        return fetchJson(`/admin/api/users/${id}`);
    }

    static edit(user: UserModification & { id: number }): Promise<void>;
    static edit(user: UserModification & { id: null }): Promise<User>;
    static async edit(user: UserModification): Promise<void | User> {
        const url = user.id != null
            ? '/admin/api/users/edit'
            : '/admin/api/users/new?baseUrl=' + encodeURIComponent(window.location.origin);
        const response = await postJson(url, user);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        if (user.id != null) {
            return;
        }
        return response.json();
    }

    static async check(user: UserModification): Promise<UserValidationResult> {
        const response = await postJson('/admin/api/users/check', user);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
    }

    static async delete(id: number): Promise<void> {
        const response = await callDelete(`/admin/api/users/${id}`);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
    }

    static async enable(id: number, status: boolean): Promise<void> {
        const response = await postJson(`/admin/api/users/${id}/enable/${status}`, null);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
    }

    static async resetPassword(id: number): Promise<User> {
        const url = `/admin/api/users/${id}/reset-password?baseUrl=${encodeURIComponent(window.location.origin)}`;
        const response = await putJson(url, null);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
    }

    static async retrieveSystemApiKey(): Promise<string> {
        const response = await fetch('/admin/api/system/api-key', {
            method: 'GET',
            credentials: 'include',
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
    }

    static async rotateSystemApiKey(): Promise<string> {
        const response = await putJson('/admin/api/system/api-key', null);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        return response.json();
    }

    static async bulkImportApiKeys(payload: BulkImportPayload): Promise<void> {
        const response = await postJson('/admin/api/api-keys/bulk', payload);
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
    }

    static loadCurrentUser(): Promise<UserModification> {
        return fetchJson('/admin/api/users/current');
    }

    static loadRoles(): Promise<Role[]> {
        return fetchJson('/admin/api/roles');
    }

    static loadOrganizations(): Promise<Organization[]> {
        return fetchJson('/admin/api/organizations');
    }
}
