import { RoleType } from "./user";

export interface Role {
  role: RoleType;
  target: string[];
  description: string;
}
