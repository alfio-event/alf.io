import Papa from 'papaparse';
import {Group, GroupMemberModification, GroupModification, LinkedGroup} from '../model/group.ts';
import {callDelete, fetchJson, postJson, responseErrorMessage} from './helpers.ts';

export const GROUP_CSV_EMAIL_COLUMN = 'email';
export const GROUP_CSV_DESCRIPTION_COLUMN = 'description';

/**
 * The result of parsing a members CSV file: the new members, and how many rows were skipped
 */
export interface ParsedGroupMembers {
    items: GroupMemberModification[];
    skippedRows: number;
}

export class GroupService {

    static loadGroups(organizationId: number): Promise<readonly Group[]> {
        return fetchJson(`/admin/api/group/for/${organizationId}`);
    }

    /**
     * The group linked to a category of an event, or null when there is none
     */
    static async loadCategoryLink(eventName: string, categoryId: number): Promise<LinkedGroup | null> {
        const response = await fetch(`/admin/api/group/for/event/${eventName}/category/${categoryId}`, {credentials: 'include'});
        if (response.status === 204) {
            return null;
        }
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'Failed to load the group of the category'));
        }
        return response.json();
    }

    /**
     * Links a group to an event or to one of its categories, or updates the link
     */
    static async link(link: LinkedGroup): Promise<void> {
        const response = await postJson(`/admin/api/group/${link.groupId}/link`, link);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'Failed to link the group'));
        }
    }

    static async unlink(organizationId: number, eventId: number, linkId: number, categoryId: number | null): Promise<void> {
        let url = `/admin/api/group/for/${organizationId}/event/${eventId}/link/${linkId}`;
        if (categoryId != null) {
            url += `?categoryId=${categoryId}`;
        }
        const response = await callDelete(url);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'Failed to remove the group'));
        }
    }

    static loadGroupDetail(organizationId: number, groupId: number): Promise<GroupModification> {
        return fetchJson(`/admin/api/group/for/${organizationId}/detail/${groupId}`);
    }

    /**
     * @return the id of the created group. A 400 answer carries the duplicates found, as plain text
     */
    static async create(organizationId: number, group: GroupModification): Promise<number> {
        const response = await postJson(`/admin/api/group/for/${organizationId}/new`, group);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'Failed to create the group'));
        }
        return Number(await response.text());
    }

    static async update(organizationId: number, groupId: number, group: GroupModification): Promise<void> {
        const response = await postJson(`/admin/api/group/for/${organizationId}/update/${groupId}`, group);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'Failed to update the group'));
        }
    }

    static async deactivateGroup(organizationId: number, groupId: number): Promise<void> {
        const response = await callDelete(`/admin/api/group/for/${organizationId}/id/${groupId}`);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'Failed to delete the group'));
        }
    }

    static async deactivateMember(organizationId: number, groupId: number, memberId: number): Promise<void> {
        const response = await callDelete(`/admin/api/group/for/${organizationId}/id/${groupId}/member/${memberId}`);
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, 'Failed to remove the member'));
        }
    }

    /**
     * The template of the members CSV file: a header with the columns email and description
     */
    static csvTemplate(): string {
        return Papa.unparse([[GROUP_CSV_EMAIL_COLUMN, GROUP_CSV_DESCRIPTION_COLUMN]], {quotes: true}) + '\r\n';
    }

    /**
     * Parses a members CSV file with header, read by column name. Rows without a valid e-mail address are skipped.
     */
    static parseCsv(content: string): ParsedGroupMembers {
        const parsed = Papa.parse<string[]>(content, {
            header: false,
            skipEmptyLines: true,
            delimiter: ',',
            // same as the other CSV imports: quotes within a quoted value are escaped by doubling them, as spreadsheets do
            quoteChar: '"',
        });
        const [header = [], ...rows] = parsed.data;
        const columnIndex = (name: string): number => header.findIndex(h => h.trim().toLowerCase() === name);
        const emailColumn = columnIndex(GROUP_CSV_EMAIL_COLUMN);
        if (emailColumn < 0) {
            throw new Error(`The file must have a "${GROUP_CSV_EMAIL_COLUMN}" column`);
        }
        const descriptionColumn = columnIndex(GROUP_CSV_DESCRIPTION_COLUMN);
        const items: GroupMemberModification[] = [];
        let skippedRows = 0;
        for (const row of rows) {
            const value = (row[emailColumn] ?? '').trim();
            if (value === '' || !EMAIL_PATTERN.test(value)) {
                skippedRows++;
                continue;
            }
            let description: string | null = '';
            if (descriptionColumn >= 0) {
                description = (row[descriptionColumn] ?? '').trim();
            }
            if (description === '') {
                description = null;
            }
            items.push({id: null, value, description});
        }
        return {items, skippedRows};
    }
}

// the rule of <input type="email"> (WHATWG HTML), same as emailAddress() in components/form-validation.ts
const EMAIL_PATTERN = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$/;
