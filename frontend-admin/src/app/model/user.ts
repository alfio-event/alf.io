export interface UserInfo {
    id: number;
    type: UserType;
    username: string;
    description: string;
    firstName: string;
    lastName: string;
    emailAddress: string;
    role: Role;
}

export enum UserType {
    INTERNAL = 'INTERNAL',
    DEMO = 'DEMO',
    API_KEY = 'API_KEY'
}

export enum Role {
    ADMIN = 'ADMIN',
    OWNER = 'OWNER',
    SUPERVISOR = 'SUPERVISOR',
    OPERATOR = 'OPERATOR',
    SPONSOR = 'SPONSOR',
    API_CONSUMER = 'API_CONSUMER'
}
