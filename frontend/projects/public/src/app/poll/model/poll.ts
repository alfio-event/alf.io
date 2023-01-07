export class Poll {
    id: number;
    status: PollStatus;
    title: {[key: string]: string };
    description: {[key: string]: string };
    allowedTags: string[];
    order: number;
    eventId: number;
    organizationId: number;
}

export type PollStatus = 'DRAFT' | 'OPEN' | 'CLOSED'