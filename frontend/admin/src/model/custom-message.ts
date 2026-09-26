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
}
