export interface Organization {
    id: number;
    name: string;
    email: string;
    description: string;
    externalId: string | null;
    slug: string | null;
}

export interface OrganizationModification {
    id: number | null;
    name: string;
    email: string;
    description: string;
    externalId: string | null;
    slug: string | null;
}
