export interface Credentials {
  username: string;
  password: string;
  _csrf: string | null;
}

export interface AuthenticationResult {
  errorMessage?: string;
  success: boolean;
}
