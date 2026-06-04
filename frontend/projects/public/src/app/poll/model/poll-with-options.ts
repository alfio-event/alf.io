import type { Poll } from "./poll";
import type { PollOption } from "./poll-option";

export class PollWithOptions {
  poll: Poll;
  options: PollOption[];
}
