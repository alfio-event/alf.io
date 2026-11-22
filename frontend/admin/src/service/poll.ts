import {Poll, PollParticipant, PollStatistics} from '../model/poll.ts';
import {callDelete, ensureOk, fetchJson, postJson, putJson, responseErrorMessage} from './helpers.ts';

/**
 * The poll admin API (PollAdminApiController): the answers are plain JSON, not Result wrappers.
 * Mutating operations reject with the error message returned by the backend.
 */
export class PollService {

    static loadList(eventName: string): Promise<Poll[]> {
        return fetchJson(`/admin/api/${encodeURIComponent(eventName)}/poll`);
    }

    static load(eventName: string, pollId: number): Promise<Poll> {
        return fetchJson(`/admin/api/${encodeURIComponent(eventName)}/poll/${pollId}`);
    }

    /**
     * @returns the id of the new poll
     */
    static async create(eventName: string, poll: Poll): Promise<number> {
        return ensureOk(await postJson(`/admin/api/${encodeURIComponent(eventName)}/poll`, poll));
    }

    static async update(eventName: string, poll: Poll): Promise<Poll> {
        return ensureOk(await postJson(`/admin/api/${encodeURIComponent(eventName)}/poll/${poll.id}`, poll));
    }

    static async updateStatus(eventName: string, pollId: number, status: 'OPEN' | 'CLOSED'): Promise<Poll> {
        const response = await putJson(`/admin/api/${encodeURIComponent(eventName)}/poll/${pollId}`, {status});
        if (!response.ok) {
            throw new Error(await responseErrorMessage(response, `Unexpected status ${response.status}`));
        }
        return response.json();
    }

    static async removeOption(eventName: string, pollId: number, optionId: number): Promise<Poll> {
        return ensureOk(await callDelete(`/admin/api/${encodeURIComponent(eventName)}/poll/${pollId}/option/${optionId}`));
    }

    static async deletePoll(eventName: string, pollId: number): Promise<boolean> {
        return ensureOk(await callDelete(`/admin/api/${encodeURIComponent(eventName)}/poll/${pollId}`));
    }

    static loadParticipants(eventName: string, pollId: number): Promise<PollParticipant[]> {
        return fetchJson(`/admin/api/${encodeURIComponent(eventName)}/poll/${pollId}/allowed`);
    }

    static async addParticipants(eventName: string, pollId: number, ticketIds: number[]): Promise<boolean> {
        return ensureOk(await postJson(`/admin/api/${encodeURIComponent(eventName)}/poll/${pollId}/allow`, {ticketIds}));
    }

    /**
     * @returns the participants still allowed after the removal
     */
    static async removeParticipants(eventName: string, pollId: number, ticketIds: number[]): Promise<PollParticipant[]> {
        return ensureOk(await callDelete(`/admin/api/${encodeURIComponent(eventName)}/poll/${pollId}/allowed`, {ticketIds}));
    }

    /**
     * The attendees whose ticket matches the search term and that can still be allowed to vote
     */
    static searchTickets(eventName: string, pollId: number, filter: string): Promise<PollParticipant[]> {
        return fetchJson(`/admin/api/${encodeURIComponent(eventName)}/poll/${pollId}/filter-tickets?filter=${encodeURIComponent(filter)}`);
    }

    static loadStatistics(eventName: string, pollId: number): Promise<PollStatistics> {
        return fetchJson(`/admin/api/${encodeURIComponent(eventName)}/poll/${pollId}/stats`);
    }
}
