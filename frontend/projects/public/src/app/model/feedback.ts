export type FeedbackType = 'SUCCESS' | 'ERROR' | 'INFO';

export interface FeedbackContent {
  type?: FeedbackType;
  active: boolean;
  message?: string;
}
