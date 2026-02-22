/**
 * Get the backend API URL based on the current host
 * If accessed via localhost, use localhost:5100
 * If accessed via IP address, use that same IP with port 5100
 */
export function getBackendUrl(): string {
  const hostname = window.location.hostname;
  
  // If localhost or 127.0.0.1, use localhost:5100
  if (hostname === 'localhost' || hostname === '127.0.0.1') {
    return 'http://localhost:5100';
  }
  
  // Otherwise, use the current hostname with port 5100
  // This allows accessing from any IP on the network
  return `http://${hostname}:5100`;
}

export function getBackendApiUrl(endpoint: string): string {
  const baseUrl = getBackendUrl();
  return `${baseUrl}${endpoint}`;
}

