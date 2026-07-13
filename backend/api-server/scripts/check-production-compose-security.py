#!/usr/bin/env python3
"""Validate the effective production Compose network and credential boundaries."""

from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[3]
PRODUCTION_COMPOSE_PATH = REPO_ROOT / "docker-compose.production.yml"
MINIO_POLICY_PATH = REPO_ROOT / "backend/api-server/minio/nugulmap-app-policy.json"
DEPLOY_WORKFLOW_PATH = REPO_ROOT / ".github/workflows/deploy.yml"
SCHEMA_PATH = REPO_ROOT / "backend/api-server/src/main/resources/schema.sql"
LOOPBACK_HOST = "127.0.0.1"
REQUIRED_SERVICE_PORTS = {
    "minio": {9000, 9001},
    "mysql": {3306},
    "mysql-app-init": set(),
    "api-server": {8080},
    "popup-trend-collector": set(),
}
COMPOSE_ENV_DEFAULTS = {
    "API_SERVER_IMAGE": "ghcr.io/example/nugulmap-backend:sha-ci",
    "MINIO_ROOT_USER": "ci-minio-root",
    "MINIO_ROOT_PASSWORD": "ci-minio-root-secret-not-production",
    "AWS_ACCESS_KEY_ID": "ci-minio-app",
    "AWS_SECRET_ACCESS_KEY": "ci-minio-app-secret-not-production",
    "AWS_S3_BUCKET": "ci-bucket",
    "MYSQL_ROOT_PASSWORD": "ci-mysql-root-password",
    "MYSQL_PASSWORD": "ci-mysql-app-password",
    "MYSQL_DATABASE": "ci_database",
    "MYSQL_USERNAME": "ci_user",
    "JWT_SECRET": "Y2ktb25seS1qd3Qtc2VjcmV0LXRoYXQtaXMtbG9uZy1lbm91Z2g=",
    "GOOGLE_CLIENT_ID": "ci-google-client",
    "GOOGLE_CLIENT_SECRET": "ci-google-secret",
    "KAKAO_CLIENT_ID": "ci-kakao-client",
    "KAKAO_CLIENT_SECRET": "ci-kakao-secret",
    "NAVER_CLIENT_ID": "ci-naver-client",
    "NAVER_CLIENT_SECRET": "ci-naver-secret",
    "APPLE_CLIENT_ID": "com.nugulmap.ci",
    "APPLE_TEAM_ID": "CI12345678",
    "APPLE_KEY_ID": "CIKEY12345",
    "APPLE_PRIVATE_KEY_BASE64": "Y2ktbm90LWEtcmVhbC1wcml2YXRlLWtleQ==",
    "APPLE_TOKEN_ENCRYPTION_KEY": "Y2ktb25seS10b2tlbi1lbmNyeXB0aW9uLWtleQ==",
    "MODERATION_OPERATOR_KEY": "ci-operator-key-not-production",
    "SEOUL_CITYDATA_API_KEY": "ci-seoul-citydata-key",
    "SEOUL_CULTURE_API_KEY": "ci-seoul-culture-key",
}
MINIO_POLICY_ACTIONS = {
    "s3:GetBucketLocation",
    "s3:ListBucket",
    "s3:ListBucketMultipartUploads",
    "s3:GetObject",
    "s3:PutObject",
    "s3:DeleteObject",
    "s3:AbortMultipartUpload",
    "s3:ListMultipartUploadParts",
}
MINIO_POLICY_RESOURCES = {
    "arn:aws:s3:::__NUGULMAP_BUCKET__",
    "arn:aws:s3:::__NUGULMAP_BUCKET__/*",
}


def deployment_database_errors(workflow: str, schema: str) -> list[str]:
    errors: list[str] = []
    for marker in (
        "SOURCE_SHA: ${{ github.sha }}",
        'git checkout --detach "$SOURCE_SHA"',
        'test "$(git rev-parse HEAD)" = "$SOURCE_SHA"',
    ):
        if marker not in workflow:
            errors.append(f"deployment workflow does not pin migrations to the image source: {marker}")
    ordered_markers = (
        'gzip -t "$backup_path"',
        "backend/api-server/src/main/resources/schema.sql",
        "backend/api-server/src/main/resources/db/manual/20260710_launch_safety.sql",
        "backend/api-server/src/main/resources/db/manual/20260711_apple_token_storage.sql",
        "backend/api-server/src/main/resources/db/manual/20260711_ugc_publication_safety.sql",
        "backend/api-server/src/main/resources/db/manual/20260712_support_request_retention.sql",
        "deploy_api() {",
    )
    positions: list[int] = []
    for marker in ordered_markers:
        position = workflow.find(marker)
        if position < 0:
            errors.append(f"deployment workflow is missing database gate: {marker}")
        positions.append(position)
    if all(position >= 0 for position in positions) and positions != sorted(positions):
        errors.append("database backup, base schema, guarded migrations, and API restart are out of order")

    for table in ("users", "zone", "zone_review"):
        if f"CREATE TABLE IF NOT EXISTS `{table}`" not in schema:
            errors.append(f"canonical schema is missing fresh-database table: {table}")
    return errors


def production_compose_errors(config: dict[str, Any]) -> list[str]:
    services = config.get("services")
    if not isinstance(services, dict):
        return ["effective Compose config has no services object"]

    errors: list[str] = []
    observed: dict[str, set[int]] = {service: set() for service in REQUIRED_SERVICE_PORTS}
    for service_name, service in services.items():
        if not isinstance(service, dict):
            continue
        ports = service.get("ports")
        if not isinstance(ports, list):
            continue
        for port in ports:
            if not isinstance(port, dict) or port.get("published") in (None, ""):
                continue
            try:
                target = int(port.get("target"))
            except (TypeError, ValueError):
                errors.append(f"{service_name} has a published port without a numeric target")
                continue
            if service_name in observed:
                observed[service_name].add(target)
            if port.get("host_ip") != LOOPBACK_HOST:
                errors.append(
                    f"{service_name}:{target} must bind to {LOOPBACK_HOST}, "
                    f"got {port.get('host_ip')!r}"
                )

    for service_name, expected_ports in REQUIRED_SERVICE_PORTS.items():
        if service_name not in services:
            errors.append(f"required production service is missing: {service_name}")
            continue
        for target in sorted(expected_ports - observed[service_name]):
            errors.append(f"required loopback port is missing: {service_name}:{target}")
    return errors


def production_credential_errors(config: dict[str, Any]) -> list[str]:
    services = config.get("services")
    if not isinstance(services, dict):
        return ["effective Compose config has no services object"]

    errors: list[str] = []
    required_environment = {
        "minio": {"MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD"},
        "minio-init": {
            "MINIO_ROOT_USER",
            "MINIO_ROOT_PASSWORD",
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY",
            "AWS_S3_BUCKET",
        },
        "mysql": {
            "MYSQL_ROOT_PASSWORD",
            "MYSQL_DATABASE",
            "MYSQL_USER",
            "MYSQL_PASSWORD",
        },
        "mysql-app-init": {
            "MYSQL_ROOT_PASSWORD",
            "MYSQL_DATABASE",
            "MYSQL_USERNAME",
            "MYSQL_PASSWORD",
        },
        "api-server": {
            "MYSQL_DATABASE",
            "MYSQL_USERNAME",
            "MYSQL_PASSWORD",
            "AWS_ACCESS_KEY_ID",
            "AWS_SECRET_ACCESS_KEY",
            "AWS_S3_BUCKET",
        },
    }
    environments: dict[str, dict[str, Any]] = {}
    for service_name, required_names in required_environment.items():
        service = services.get(service_name)
        if not isinstance(service, dict):
            errors.append(f"required production service is missing: {service_name}")
            continue
        environment = service.get("environment")
        if not isinstance(environment, dict):
            errors.append(f"{service_name} has no environment mapping")
            continue
        environments[service_name] = environment
        for name in sorted(required_names):
            if environment.get(name) in (None, ""):
                errors.append(f"{service_name} is missing required environment: {name}")

    minio = environments.get("minio", {})
    minio_init = environments.get("minio-init", {})
    mysql = environments.get("mysql", {})
    mysql_init = environments.get("mysql-app-init", {})
    api = environments.get("api-server", {})

    if minio.get("MINIO_ROOT_USER") == minio_init.get("AWS_ACCESS_KEY_ID"):
        errors.append("MinIO root user must differ from the app access key")
    if minio.get("MINIO_ROOT_PASSWORD") == minio_init.get("AWS_SECRET_ACCESS_KEY"):
        errors.append("MinIO root password must differ from the app secret key")
    if mysql.get("MYSQL_ROOT_PASSWORD") == mysql.get("MYSQL_PASSWORD"):
        errors.append("MySQL root password must differ from the app password")
    if str(mysql.get("MYSQL_USER", "")).strip().lower() == "root":
        errors.append("MySQL app user must not be root")
    if not re.fullmatch(r"[A-Za-z0-9_]+", str(mysql.get("MYSQL_DATABASE", ""))):
        errors.append("MySQL database must contain only letters, numbers, and underscores")
    if not re.fullmatch(r"[A-Za-z0-9_]+", str(mysql.get("MYSQL_USER", ""))):
        errors.append("MySQL app user must contain only letters, numbers, and underscores")
    mysql_app_password = str(mysql.get("MYSQL_PASSWORD", ""))
    if len(mysql_app_password) < 16 or not re.fullmatch(r"[A-Za-z0-9_+./=~-]+", mysql_app_password):
        errors.append("MySQL app password must be at least 16 URL/base64-safe characters")

    matching_values = (
        ("MinIO root user", minio.get("MINIO_ROOT_USER"), minio_init.get("MINIO_ROOT_USER")),
        ("MinIO root password", minio.get("MINIO_ROOT_PASSWORD"), minio_init.get("MINIO_ROOT_PASSWORD")),
        ("MinIO app access key", minio_init.get("AWS_ACCESS_KEY_ID"), api.get("AWS_ACCESS_KEY_ID")),
        ("MinIO app secret key", minio_init.get("AWS_SECRET_ACCESS_KEY"), api.get("AWS_SECRET_ACCESS_KEY")),
        ("MinIO bucket", minio_init.get("AWS_S3_BUCKET"), api.get("AWS_S3_BUCKET")),
        ("MySQL database", mysql.get("MYSQL_DATABASE"), api.get("MYSQL_DATABASE")),
        ("MySQL app user", mysql.get("MYSQL_USER"), api.get("MYSQL_USERNAME")),
        ("MySQL app password", mysql.get("MYSQL_PASSWORD"), api.get("MYSQL_PASSWORD")),
        ("MySQL init root password", mysql.get("MYSQL_ROOT_PASSWORD"), mysql_init.get("MYSQL_ROOT_PASSWORD")),
        ("MySQL init database", mysql.get("MYSQL_DATABASE"), mysql_init.get("MYSQL_DATABASE")),
        ("MySQL init app user", mysql.get("MYSQL_USER"), mysql_init.get("MYSQL_USERNAME")),
        ("MySQL init app password", mysql.get("MYSQL_PASSWORD"), mysql_init.get("MYSQL_PASSWORD")),
    )
    for label, initializer_value, runtime_value in matching_values:
        if initializer_value not in (None, "") and runtime_value not in (None, ""):
            if initializer_value != runtime_value:
                errors.append(f"{label} differs between initializer and runtime services")

    for forbidden_name in ("MINIO_ROOT_USER", "MINIO_ROOT_PASSWORD", "MYSQL_ROOT_PASSWORD"):
        if forbidden_name in api:
            errors.append(f"api-server must not receive privileged credential: {forbidden_name}")

    minio_init_service = services.get("minio-init")
    if isinstance(minio_init_service, dict):
        entrypoint = json.dumps(minio_init_service.get("entrypoint", ""))
        for required_fragment in (
            "mc anonymous set none",
            "mc admin user add",
            "mc admin policy create",
            "mc admin policy attach",
        ):
            if required_fragment not in entrypoint:
                errors.append(f"minio-init entrypoint is missing: {required_fragment}")
        if "anonymous set public" in entrypoint:
            errors.append("minio-init must not grant anonymous public bucket access")

        volumes = minio_init_service.get("volumes")
        policy_mount = False
        if isinstance(volumes, list):
            policy_mount = any(
                isinstance(volume, dict)
                and volume.get("target") == "/config/nugulmap-app-policy.json"
                and volume.get("read_only") is True
                for volume in volumes
            )
        if not policy_mount:
            errors.append("minio-init must mount the app policy read-only")

    mysql_init_service = services.get("mysql-app-init")
    if isinstance(mysql_init_service, dict):
        entrypoint = json.dumps(mysql_init_service.get("entrypoint", ""))
        for required_fragment in (
            "MYSQL_ROOT_PASSWORD must differ from MYSQL_PASSWORD",
            "MYSQL_USERNAME must be a non-root",
            "REVOKE ALL PRIVILEGES, GRANT OPTION",
            "GRANT SELECT, INSERT, UPDATE, DELETE",
        ):
            if required_fragment not in entrypoint:
                errors.append(f"mysql-app-init entrypoint is missing: {required_fragment}")
        if "GRANT ALL PRIVILEGES ON" in entrypoint:
            errors.append("mysql-app-init must not grant all privileges to the app user")

    api_service = services.get("api-server")
    if isinstance(api_service, dict):
        depends_on = api_service.get("depends_on")
        mysql_init_dependency = depends_on.get("mysql-app-init") if isinstance(depends_on, dict) else None
        if not isinstance(mysql_init_dependency, dict) or (
            mysql_init_dependency.get("condition") != "service_completed_successfully"
        ):
            errors.append("api-server must wait for successful mysql-app-init completion")

    return errors


def production_overlay_contract_errors(content: str) -> list[str]:
    errors: list[str] = []
    required_markers = {
        "MINIO_ROOT_USER": "MINIO_ROOT_USER: ${MINIO_ROOT_USER:?",
        "MINIO_ROOT_PASSWORD": "MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD:?",
        "MYSQL_ROOT_PASSWORD": "MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?",
        "MYSQL_USER": "MYSQL_USER: ${MYSQL_USERNAME:?",
        "MYSQL_PASSWORD": "MYSQL_PASSWORD: ${MYSQL_PASSWORD:?",
    }
    for name, marker in required_markers.items():
        if marker not in content:
            errors.append(f"production overlay does not fail fast for {name}")
    for forbidden in (
        "MINIO_ROOT_USER: ${AWS_ACCESS_KEY_ID",
        "MINIO_ROOT_PASSWORD: ${AWS_SECRET_ACCESS_KEY",
        "MYSQL_ROOT_PASSWORD: ${MYSQL_PASSWORD",
    ):
        if forbidden in content:
            errors.append(f"production overlay reuses a privileged credential: {forbidden}")
    return errors


def minio_policy_errors(policy: dict[str, Any]) -> list[str]:
    statements = policy.get("Statement")
    if not isinstance(statements, list) or not statements:
        return ["MinIO app policy has no statements"]

    errors: list[str] = []
    actions: set[str] = set()
    resources: set[str] = set()
    for statement in statements:
        if not isinstance(statement, dict):
            errors.append("MinIO app policy contains a non-object statement")
            continue
        if statement.get("Effect") != "Allow":
            errors.append("MinIO app policy statements must use Effect=Allow")
        statement_actions = statement.get("Action")
        statement_resources = statement.get("Resource")
        if not isinstance(statement_actions, list) or not all(
            isinstance(value, str) for value in statement_actions
        ):
            errors.append("MinIO app policy statement has invalid actions")
        else:
            actions.update(statement_actions)
        if not isinstance(statement_resources, list) or not all(
            isinstance(value, str) for value in statement_resources
        ):
            errors.append("MinIO app policy statement has invalid resources")
        else:
            resources.update(statement_resources)

    if actions != MINIO_POLICY_ACTIONS:
        errors.append("MinIO app policy actions are not the approved runtime-only set")
    if resources != MINIO_POLICY_RESOURCES:
        errors.append("MinIO app policy resources are not scoped to the configured bucket")
    return errors


def render_effective_production_compose() -> dict[str, Any]:
    environment = os.environ.copy()
    for name, value in COMPOSE_ENV_DEFAULTS.items():
        environment.setdefault(name, value)
    command = [
        "docker",
        "compose",
        "-f",
        "docker-compose.yml",
        "-f",
        "docker-compose.production.yml",
        "config",
        "--format",
        "json",
        "--no-env-resolution",
    ]
    result = subprocess.run(
        command,
        cwd=REPO_ROOT,
        env=environment,
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip() or f"exit {result.returncode}"
        raise RuntimeError(f"docker compose config failed: {detail}")
    payload = json.loads(result.stdout)
    if not isinstance(payload, dict):
        raise RuntimeError("docker compose config did not return a JSON object")
    return payload


def main() -> int:
    try:
        config = render_effective_production_compose()
    except (OSError, RuntimeError, json.JSONDecodeError) as error:
        print(f"[FAIL] production-compose-render: {error}", file=sys.stderr)
        return 2

    errors = production_compose_errors(config)
    errors.extend(production_credential_errors(config))
    try:
        overlay_content = PRODUCTION_COMPOSE_PATH.read_text(encoding="utf-8")
        errors.extend(production_overlay_contract_errors(overlay_content))
    except OSError as error:
        errors.append(f"cannot read production Compose overlay: {error}")
    try:
        policy = json.loads(MINIO_POLICY_PATH.read_text(encoding="utf-8"))
        if not isinstance(policy, dict):
            errors.append("MinIO app policy is not a JSON object")
        else:
            errors.extend(minio_policy_errors(policy))
    except (OSError, json.JSONDecodeError) as error:
        errors.append(f"cannot read MinIO app policy: {error}")
    try:
        workflow = DEPLOY_WORKFLOW_PATH.read_text(encoding="utf-8")
        schema = SCHEMA_PATH.read_text(encoding="utf-8")
        errors.extend(deployment_database_errors(workflow, schema))
    except OSError as error:
        errors.append(f"cannot read production database deployment contract: {error}")
    if errors:
        for error in errors:
            print(f"[FAIL] production-compose-security: {error}", file=sys.stderr)
        return 1

    print("[PASS] production Compose security and ordered database migration contracts are enforced")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
