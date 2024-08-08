export type FeedbackType = 'SUCCESS' | 'ERROR' | 'INFO';

export type FeedbackContent = {
  type?: FeedbackType;
  active: boolean;
  message?: string;
}
