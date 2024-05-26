export interface Organization {
    id: number;
    name: string;
    email: string;
    externalId: string | null;
    slug: string | null;
}
