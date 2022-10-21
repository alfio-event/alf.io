export class ValidatedResponse<T> {
  success: boolean;
  errorCount: number;
  validationErrors: ErrorDescriptor[];
  value: T;
  warnings: Array<WarningMessage>;
}

export class ErrorDescriptor {
  fieldName: string;
  code: string;
  arguments: {[key: string]: any};
}

export interface WarningMessage {
  code: string;
  params: Array<string>;
}
