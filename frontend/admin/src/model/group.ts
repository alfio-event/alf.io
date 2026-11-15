/**
 * A group of e-mail addresses, as returned by the list endpoint (alfio.model.group.Group)
 */
export interface Group {
    id: number;
    name: string;
    description: string;
    organizationId: number;
    active: boolean;
}

/**
 * A member of a group (alfio.model.modification.GroupMemberModification): the value is the e-mail address.
 * A member already saved on the server has an id; a new one, added in the form, has id null.
 */
export interface GroupMemberModification {
    id: number | null;
    value: string | null;
    description: string | null;
}

/**
 * The payload of the create/update endpoints (alfio.model.modification.GroupModification)
 */
export interface GroupModification {
    id: number | null;
    name: string;
    description: string;
    organizationId: number;
    items: GroupMemberModification[];
}

export type LinkedGroupType = 'ONCE_PER_VALUE' | 'LIMITED_QUANTITY' | 'UNLIMITED';
export type LinkedGroupMatchType = 'FULL' | 'EMAIL_DOMAIN';

/**
 * The link between a group and an event or one of its categories (alfio.model.group.LinkedGroup):
 * only the members of the group can buy tickets. A link not saved yet has no id
 */
export interface LinkedGroup {
    id?: number | null;
    groupId: number;
    eventId: number;
    ticketCategoryId: number | null;
    type: LinkedGroupType;
    matchType: LinkedGroupMatchType;
    maxAllocation: number | null;
}
