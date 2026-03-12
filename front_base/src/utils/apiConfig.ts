/**
 * Get the backend API URL based on the current host
 * If accessed via localhost, use localhost:5100
 * If accessed via IP address, use that same IP with port 5100
 */
export function getBackendUrl(): string {
  const hostname = window.location.hostname;
  
  return 'http://192.168.1.131:5001';
  return 'http://bean-imgshare-spring-backend-env.eba-r8jkiipd.eu-central-1.elasticbeanstalk.com';
  // If localhost or 127.0.0.1, use localhost:5100
  if (hostname === 'localhost' || hostname === '127.0.0.1') {
    return 'option1';
  }
  
  // Otherwise, use the current hostname with port 5100
  // This allows accessing from any IP on the network
  return `option2`;
}

export function getBackendApiUrl(endpoint: string): string {
  const baseUrl = getBackendUrl();
  return `${baseUrl}${endpoint}`;
}

