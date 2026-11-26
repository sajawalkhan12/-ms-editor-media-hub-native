import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function base64Url(bytes: Uint8Array | string): string {
  const binary = typeof bytes === "string"
    ? bytes
    : Array.from(bytes).map((b) => String.fromCharCode(b)).join("");
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function pemToBytes(pem: string): Uint8Array {
  const base64 = pem.replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "").replace(/\s/g, "");
  const binary = atob(base64);
  return Uint8Array.from(binary, (c) => c.charCodeAt(0));
}

async function googleAccessToken(serviceAccount: Record<string, string>): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64Url(JSON.stringify({
    iss: serviceAccount.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = header + "." + claims;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(serviceAccount.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = new Uint8Array(await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(unsigned),
  ));
  const assertion = unsigned + "." + base64Url(signature);
  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });
  const tokenBody = await tokenResponse.json();
  if (!tokenResponse.ok || !tokenBody.access_token) {
    throw new Error("Google OAuth token error: " + JSON.stringify(tokenBody));
  }
  return tokenBody.access_token as string;
}

async function restGet(url: string, serviceKey: string) {
  const response = await fetch(url, {
    headers: { apikey: serviceKey, Authorization: "Bearer " + serviceKey },
  });
  const text = await response.text();
  if (!response.ok) throw new Error("Supabase REST " + response.status + ": " + text);
  return JSON.parse(text);
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "POST required" }, 405);

  try {
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const publishableKeys = Deno.env.get("SUPABASE_PUBLISHABLE_KEYS");
    const secretKeys = Deno.env.get("SUPABASE_SECRET_KEYS");
    const anonKey = Deno.env.get("SUPABASE_ANON_KEY") ??
      Deno.env.get("SUPABASE_PUBLISHABLE_KEY") ??
      (publishableKeys ? JSON.parse(publishableKeys).default : "");
    const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ??
      Deno.env.get("SUPABASE_SECRET_KEY") ??
      (secretKeys ? JSON.parse(secretKeys).default : "");
    if (!anonKey || !serviceKey) throw new Error("Supabase Edge Function API keys are unavailable");
    const authHeader = req.headers.get("Authorization") ?? "";
    const userResponse = await fetch(supabaseUrl + "/auth/v1/user", {
      headers: { apikey: anonKey, Authorization: authHeader },
    });
    if (!userResponse.ok) return json({ error: "Invalid user session" }, 401);
    const user = await userResponse.json();
    const senderId = user.id as string;

    const { message_id } = await req.json();
    if (!message_id) return json({ error: "message_id required" }, 400);

    const messageRows = await restGet(
      supabaseUrl + "/rest/v1/messages?select=id,sender_id,recipient_id,message&id=eq." + encodeURIComponent(message_id) + "&limit=1",
      serviceKey,
    );
    const message = messageRows[0];
    if (!message || message.sender_id !== senderId) return json({ error: "Message not found or not owned by sender" }, 403);

    const senderProfiles = await restGet(
      supabaseUrl + "/rest/v1/profiles?select=username,avatar_path&id=eq." + encodeURIComponent(senderId) + "&limit=1",
      serviceKey,
    );
    const senderProfile = senderProfiles[0] ?? {};
    const recipientTokens = await restGet(
      supabaseUrl + "/rest/v1/user_push_tokens?select=fcm_token&user_id=eq." + encodeURIComponent(message.recipient_id),
      serviceKey,
    );
    if (!recipientTokens.length) return json({ sent: 0, message: "Recipient has no registered device token" });

    const accountJson = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
    if (!accountJson) return json({ error: "FIREBASE_SERVICE_ACCOUNT_JSON secret is not set in Supabase Edge Function secrets" }, 503);
    const serviceAccount = JSON.parse(accountJson);
    const accessToken = await googleAccessToken(serviceAccount);
    const avatarPath = (senderProfile.avatar_path ?? "").replace(/^\//, "");
    const avatarUrl = avatarPath
      ? supabaseUrl.replace(/\/$/, "") + "/storage/v1/object/public/profile-images/" +
        avatarPath.split("/").map((part: string) => encodeURIComponent(part)).join("/")
      : "";

    let sent = 0;
    const errors: string[] = [];
    for (const row of recipientTokens) {
      const payload = {
        message: {
          token: row.fcm_token,
          android: { priority: "HIGH" },
          data: {
            type: "message",
            message_id: String(message.id),
            sender_id: String(senderId),
            sender_username: String(senderProfile.username ?? "user"),
            sender_avatar_url: avatarUrl,
            recipient_id: String(message.recipient_id),
            body: String(message.message).slice(0, 400),
          },
        },
      };
      const fcmResponse = await fetch(
        "https://fcm.googleapis.com/v1/projects/" + encodeURIComponent(serviceAccount.project_id) + "/messages:send",
        {
          method: "POST",
          headers: { Authorization: "Bearer " + accessToken, "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        },
      );
      if (fcmResponse.ok) sent++;
      else errors.push(await fcmResponse.text());
    }
    return json({ sent, errors });
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : String(error) }, 500);
  }
});
