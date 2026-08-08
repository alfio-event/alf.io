export type UserType = 'user' | 'apikey';

export interface Role {
    role: string;
    description: string;
    target: string[];
}

export interface User {
    id: number;
    username: string;
    firstName: string;
    lastName: string;
    emailAddress: string;
    description: string;
    type: string;
    enabled: boolean;
    roles: string[];
    memberOf: OrganizationRef[];
    password?: string;
    qrCode?: string;
}

interface OrganizationRef {
    id: number;
    name: string;
}

export interface UserModification {
    id: number | null;
    organizationId: number;
    role: string;
    username?: string;
    firstName?: string;
    lastName?: string;
    emailAddress?: string;
    type?: string;
    description?: string;
}

export interface BulkImportPayload {
    organizationId: number | null;
    role: string;
    descriptions: string[];
}

export interface PasswordModification {
    oldPassword: string;
    newPassword: string;
    newPasswordConfirm: string;
}
