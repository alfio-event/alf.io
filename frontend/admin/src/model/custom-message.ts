export interface MessageModification {
    locale: string;
    subject: string;
    text: string;
    subjectExample: string;
    textExample: string;
    attachTicket: boolean;
}

export interface MessagePreview {
    affectedUsers: number;
    preview: MessageModification[];
    /**
     * the HTML version of the e-mail, by locale. Missing if the message will be sent as plain text
     */
    htmlPreview?: Record<string, string>;
}
