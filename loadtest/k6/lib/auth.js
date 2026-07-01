import http from "k6/http";
import { check } from "k6";
import { Rate } from "k6/metrics";

export const registerFail = new Rate("lt_register_fail");
export const wsTokenFail = new Rate("lt_ws_token_fail");

const JSON_HEADERS = { "Content-Type": "application/json" };

/**
 * Claim a device for a team and return its tokens. This is the public check-in path real apps
 * use, so it also load-tests the check-in surge.
 */
export function registerDevice(base, gameId, teamId, nickname) {
  const res = http.post(
    `${base}/api/v1/check-in/register-device`,
    JSON.stringify({ gameId, teamId, nickname }),
    { headers: JSON_HEADERS, tags: { name: "register-device" } },
  );
  const ok = check(res, { "register-device 200": (r) => r.status === 200 });
  registerFail.add(!ok);
  if (!ok) return null;
  return {
    deviceId: res.json("deviceId"),
    accessToken: res.json("accessToken"),
    refreshToken: res.json("refreshToken"),
  };
}

/** Fetch a short-lived WebSocket token for a team. */
export function wsToken(base, accessToken, gameId, teamId) {
  const res = http.get(`${base}/api/v1/ws/token/team/${gameId}/${teamId}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    tags: { name: "ws-token" },
  });
  const ok = check(res, { "ws-token 200": (r) => r.status === 200 });
  wsTokenFail.add(!ok);
  return ok ? res.json("token") : null;
}

/** Rotate tokens for long runs (access token lives ~1h). */
export function refresh(base, refreshToken) {
  const res = http.post(
    `${base}/api/v1/auth/refresh`,
    JSON.stringify({ refreshToken }),
    { headers: JSON_HEADERS, tags: { name: "refresh" } },
  );
  if (res.status !== 200) return null;
  return { accessToken: res.json("accessToken"), refreshToken: res.json("refreshToken") };
}
