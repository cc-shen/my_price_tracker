const API_BASE_URL = process.env.API_BASE_URL ?? "http://127.0.0.1:3000";

const normalizeBaseUrl = (baseUrl: string) => baseUrl.replace(/\/$/, "");

const buildUrl = (path: string) => {
  const base = normalizeBaseUrl(API_BASE_URL);
  return `${base}${path.startsWith("/") ? path : `/${path}`}`;
};

const defaultHeaders = {
  "Content-Type": "application/json"
};

export async function apiGet<T>(path: string): Promise<T> {
  const response = await fetch(buildUrl(path), {
    headers: defaultHeaders
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `Request failed with ${response.status}`);
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
    const message = await response.text();
    throw new Error(message || `Request failed with ${response.status}`);
  }

  return response.json() as Promise<T>;
}
