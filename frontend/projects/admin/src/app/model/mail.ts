export interface Mail {
  left: MailInfo[];
  right: number;
}

export interface MailInfo {
  message: string;
  id: number;
  subscriptionDescriptorId: number;
  status: string;
  eventId: number;
  cc: any[];
  subject: string;
  organizationId: number;
  attempts: number;
  checksum: string;
  recipient: string;
  htmlMessage: string;
  attachments: string;
  purchaseContextType: string;
  sentTimestamp: string;
  requestTimestamp: string;
}
