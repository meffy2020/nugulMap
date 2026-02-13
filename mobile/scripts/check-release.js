#!/usr/bin/env node

const fs = require("node:fs")
const path = require("node:path")
const { execSync } = require("node:child_process")

const mobileRoot = path.resolve(__dirname, "..")
const strict = process.argv.includes("--strict")

const errors = []
const warnings = []

function readExpoConfig() {
  try {
    const output = execSync("npx expo config --type public --json", {
      cwd: mobileRoot,
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    })
    return JSON.parse(output)
  } catch (error) {
    errors.push("failed to read expo config (run `npm install` in mobile first)")
    if (error instanceof Error && error.message) {
      warnings.push(error.message.split("\n")[0])
    }
    return null
  }
}

function addError(message) {
  errors.push(message)
}

function addWarning(message) {
  warnings.push(message)
}

function assert(condition, message) {
  if (!condition) addError(message)
}

function warn(condition, message) {
  if (!condition) addWarning(message)
}

function resolveAssetPath(rawPath) {
  if (typeof rawPath !== "string" || rawPath.trim() === "") {
    return null
  }
  const normalized = rawPath.startsWith("./") ? rawPath.slice(2) : rawPath
  return path.resolve(mobileRoot, normalized)
}

function hasPlaceholder(value) {
  if (typeof value !== "string") return false
  const normalized = value.trim().toLowerCase()
  if (!normalized) return true
  return (
    normalized.includes("your_") ||
    normalized.includes("replace_") ||
    normalized.includes("todo") ||
    normalized.includes("changeme") ||
    normalized.includes("example")
  )
}

function toUrl(value) {
  if (typeof value !== "string" || value.trim() === "") return null
  try {
    return new URL(value)
  } catch {
    return null
  }
}

function isPrivateHost(hostname) {
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname.endsWith(".local")
}

function toSchemes(schemeField) {
  if (typeof schemeField === "string") {
    return [schemeField]
  }
  if (Array.isArray(schemeField)) {
    return schemeField.filter((value) => typeof value === "string" && value.length > 0)
  }
  return []
}

function validateAsset(rawPath, label) {
  assert(typeof rawPath === "string" && rawPath.length > 0, `${label} path is missing`)
  if (typeof rawPath !== "string" || rawPath.length === 0) return

  const resolved = resolveAssetPath(rawPath)
  assert(Boolean(resolved), `${label} path is invalid`)
  if (!resolved) return

  assert(fs.existsSync(resolved), `${label} file is missing at ${rawPath}`)
}

function validateConfig(config) {
  assert(typeof config.name === "string" && config.name.length > 0, "app name is missing")
  assert(typeof config.slug === "string" && config.slug.length > 0, "slug is missing")
  assert(typeof config.version === "string" && config.version.length > 0, "version is missing")

  const schemes = toSchemes(config.scheme)
  assert(schemes.length > 0, "scheme is missing (required for oauth deep links)")

  validateAsset(config.icon, "icon")
  if (config.splash && typeof config.splash === "object") {
    validateAsset(config.splash.image, "splash image")
  } else {
    addWarning("splash config is missing")
  }

  const ios = config.ios || {}
  assert(typeof ios.bundleIdentifier === "string" && ios.bundleIdentifier.includes("."), "ios.bundleIdentifier is invalid")
  assert(typeof ios.buildNumber === "string" && ios.buildNumber.length > 0, "ios.buildNumber is missing")

  const android = config.android || {}
  assert(typeof android.package === "string" && android.package.includes("."), "android.package is invalid")
  assert(Number.isInteger(android.versionCode) && android.versionCode > 0, "android.versionCode must be a positive integer")

  const extra = config.extra || {}

  if (typeof extra.apiBaseUrl !== "string" || extra.apiBaseUrl.length === 0) {
    addError("extra.apiBaseUrl is missing")
  } else {
    const apiUrl = toUrl(extra.apiBaseUrl)
    assert(Boolean(apiUrl), "extra.apiBaseUrl must be a valid URL")
    if (apiUrl) {
      assert(apiUrl.protocol === "https:", "extra.apiBaseUrl must use https")
      if (strict) {
        assert(!isPrivateHost(apiUrl.hostname), "extra.apiBaseUrl must be public for release builds")
      }
    }
  }

  if (typeof extra.kakaoJavascriptKey !== "string" || extra.kakaoJavascriptKey.length === 0) {
    addError("extra.kakaoJavascriptKey is missing")
  } else if (hasPlaceholder(extra.kakaoJavascriptKey)) {
    if (strict) {
      addError("extra.kakaoJavascriptKey looks like a placeholder")
    } else {
      addWarning("extra.kakaoJavascriptKey looks like a placeholder")
    }
  }

  if (typeof extra.kakaoWebviewBaseUrl !== "string" || extra.kakaoWebviewBaseUrl.length === 0) {
    addError("extra.kakaoWebviewBaseUrl is missing")
  } else {
    const webviewUrl = toUrl(extra.kakaoWebviewBaseUrl)
    assert(Boolean(webviewUrl), "extra.kakaoWebviewBaseUrl must be a valid URL")
    if (webviewUrl) {
      assert(webviewUrl.protocol === "https:", "extra.kakaoWebviewBaseUrl must use https")
      if (strict) {
        assert(!isPrivateHost(webviewUrl.hostname), "extra.kakaoWebviewBaseUrl must be public for release builds")
      }
    }
  }

  if (typeof extra.kakaoMarkerImageUrl === "string" && extra.kakaoMarkerImageUrl.length > 0) {
    const markerUrl = toUrl(extra.kakaoMarkerImageUrl)
    assert(Boolean(markerUrl), "extra.kakaoMarkerImageUrl must be a valid URL")
    if (markerUrl) {
      assert(markerUrl.protocol === "https:", "extra.kakaoMarkerImageUrl must use https")
    }
  } else {
    addWarning("extra.kakaoMarkerImageUrl is empty")
  }

  if (typeof extra.oauthRedirectUri !== "string" || extra.oauthRedirectUri.length === 0) {
    addError("extra.oauthRedirectUri is missing")
  } else if (schemes.length > 0) {
    const matchesAnyScheme = schemes.some((scheme) => extra.oauthRedirectUri.startsWith(`${scheme}://`))
    assert(matchesAnyScheme, "extra.oauthRedirectUri must match app scheme")
  }

  warn(fs.existsSync(path.resolve(mobileRoot, "eas.json")), "eas.json is missing")
}

function printReport() {
  const title = strict ? "release check (strict)" : "release check"
  console.log(`\n${title}`)
  console.log("-".repeat(title.length))

  if (warnings.length > 0) {
    console.log("\nwarnings")
    warnings.forEach((warning, index) => {
      console.log(`${index + 1}. ${warning}`)
    })
  }

  if (errors.length > 0) {
    console.log("\nerrors")
    errors.forEach((error, index) => {
      console.log(`${index + 1}. ${error}`)
    })
    process.exit(1)
  }

  console.log("\nPASS: app-store/play-store minimum checks satisfied")
}

const config = readExpoConfig()
if (config) {
  validateConfig(config)
}
printReport()
