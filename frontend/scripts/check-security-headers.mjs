import nextConfig from "../next.config.mjs"

const requiredHeaders = new Set([
  "strict-transport-security",
  "x-content-type-options",
  "x-frame-options",
  "referrer-policy",
  "permissions-policy",
])

if (nextConfig.poweredByHeader !== false) {
  throw new Error("Next.js powered-by header must remain disabled in production")
}

const rules = await nextConfig.headers()
const catchAll = rules.find((rule) => rule.source === "/(.*)")
if (!catchAll) {
  throw new Error("A catch-all production security header rule is required")
}

const configured = new Map(
  catchAll.headers.map(({ key, value }) => [key.toLowerCase(), value]),
)
const missing = [...requiredHeaders].filter((key) => !configured.has(key))
if (missing.length > 0) {
  throw new Error(`Missing production security headers: ${missing.join(", ")}`)
}

if (!configured.get("strict-transport-security")?.includes("includeSubDomains")) {
  throw new Error("HSTS must cover subdomains")
}
if (configured.get("x-frame-options") !== "DENY") {
  throw new Error("Policy pages must not be frameable")
}

console.log("Production security header contract: PASS")
