export interface Organization {
  id: number;
  name: string;
  description: string;
  email: string;
  externalId: string | null,
  slug: string | null,
}
