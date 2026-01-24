const API_BASE_URL = process.env.API_BASE_URL ?? "http://127.0.0.1:3000";

const normalizeBaseUrl = (baseUrl: string) => baseUrl.replace(/\/$/, "");

const buildUrl = (path: string) => {
  const base = normalizeBaseUrl(API_BASE_URL);
  return `${base}${path.startsWith("/") ? path : `/${path}`}`;
};

const defaultHeaders = {
  "Content-Type": "application/json"
};

export class ApiError extends Error {
  status: number;
  type?: string;
  details?: unknown;

  constructor(status: number, message: string, type?: string, details?: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.type = type;
    this.details = details;
  }
}

const parseErrorResponse = async (response: Response) => {
  const text = await response.text();
  if (text) {
    try {
      const data = JSON.parse(text) as {
        error?: { message?: string; type?: string; details?: unknown };
      };
      if (data?.error?.message) {
        return {
          message: data.error.message,
          type: data.error.type,
          details: data.error.details
        };
      }
    } catch {
      // Fall back to plain text.
    }
  }
  return {
    message: text || `Request failed with ${response.status}`
  };
};

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(buildUrl(path), {
    headers: defaultHeaders
  });

  if (!response.ok) {
    const errorInfo = await parseErrorResponse(response);
    throw new ApiError(response.status, errorInfo.message, errorInfo.type, errorInfo.details);
  }

  return response.json() as Promise<T>;
}

export async function apiSend<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const response = await fetch(buildUrl(path), {
    ...options,
    headers: {
      ...defaultHeaders,
      ...options.headers
    }
  });

  if (!response.ok) {
    const errorInfo = await parseErrorResponse(response);
    throw new ApiError(response.status, errorInfo.message, errorInfo.type, errorInfo.details);
  }

  if (response.status === 204) {
    return null as T;
  }

  const bodyText = await response.text();
  if (!bodyText) {
    return null as T;
  }

  return JSON.parse(bodyText) as T;
}
