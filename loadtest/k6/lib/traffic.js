import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";

export const picturesUploaded = new Counter("lt_pictures_uploaded");

/**
 * One round of the read-heavy polling a live player app does on its dashboard. Requests are
 * tagged by {@code name} so k6 reports per-endpoint latency and error rate.
 */
export function pollDashboard(base, session, gameId, teamId) {
  const authHeaders = { Authorization: `Bearer ${session.accessToken}` };
  const reqs = [
    { method: "GET", url: `${base}/api/v1/team/me`, params: { headers: authHeaders, tags: { name: "team-me" } } },
    { method: "GET", url: `${base}/api/v1/teams/${teamId}/achievements`, params: { headers: authHeaders, tags: { name: "achievements" } } },
    { method: "GET", url: `${base}/api/v1/teams/${teamId}/achievements/verified`, params: { headers: authHeaders, tags: { name: "achievements-verified" } } },
    { method: "GET", url: `${base}/api/v1/messages/team/${teamId}/unread`, params: { headers: authHeaders, tags: { name: "messages-unread" } } },
    { method: "GET", url: `${base}/api/v1/games/${gameId}/rankings`, params: { headers: authHeaders, tags: { name: "rankings" } } },
  ];
  const responses = http.batch(reqs);
  for (const r of responses) {
    check(r, { "poll <400": (res) => res.status < 400 });
  }
}

/** Occasional map/checkpoint read (real apps hit this less often than the dashboard). */
export function pollCheckpoints(base, session, gameId) {
  const res = http.get(`${base}/api/v1/games/${gameId}/checkpoints`, {
    headers: { Authorization: `Bearer ${session.accessToken}` },
    tags: { name: "checkpoints" },
  });
  check(res, { "checkpoints <400": (r) => r.status < 400 });
}

/**
 * Full picture-upload flow: reserve a presigned URL, PUT bytes straight to S3, then confirm.
 * Only a small fraction of app sessions do this, mirroring real usage.
 */
export function uploadPicture(base, session, teamId, bytes) {
  const authHeaders = { Authorization: `Bearer ${session.accessToken}`, "Content-Type": "application/json" };
  const reserve = http.post(
    `${base}/api/v1/teams/${teamId}/pictures`,
    JSON.stringify({ contentType: "image/jpeg" }),
    { headers: authHeaders, tags: { name: "picture-reserve" } },
  );
  if (!check(reserve, { "picture-reserve 2xx": (r) => r.status >= 200 && r.status < 300 })) return;

  const pictureId = reserve.json("pictureId");
  const uploadUrl = reserve.json("uploadUrl");

  const put = http.put(uploadUrl, bytes, {
    headers: { "Content-Type": "image/jpeg" },
    tags: { name: "picture-s3-put" },
  });
  if (!check(put, { "picture-s3 2xx": (r) => r.status >= 200 && r.status < 300 })) return;

  const confirm = http.post(
    `${base}/api/v1/teams/${teamId}/pictures/${pictureId}/confirm`,
    null,
    { headers: { Authorization: `Bearer ${session.accessToken}` }, tags: { name: "picture-confirm" } },
  );
  if (check(confirm, { "picture-confirm 2xx": (r) => r.status >= 200 && r.status < 300 })) {
    picturesUploaded.add(1);
  }
}
