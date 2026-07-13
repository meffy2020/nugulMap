import importlib.util
import json
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("check-production-compose-security.py")
SPEC = importlib.util.spec_from_file_location("production_compose_security", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
security = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(security)


class ProductionComposeSecurityTest(unittest.TestCase):
    def test_accepts_only_loopback_host_bindings_for_published_ports(self) -> None:
        config = {
            "services": {
                "minio": {"ports": [self.port(9000), self.port(9001)]},
                "mysql": {"ports": [self.port(3306)]},
                "mysql-app-init": {},
                "api-server": {"ports": [self.port(8080)]},
                "popup-trend-collector": {},
            }
        }

        self.assertEqual([], security.production_compose_errors(config))

    def test_rejects_wildcard_or_missing_host_bindings(self) -> None:
        config = {
            "services": {
                "minio": {"ports": [self.port(9000, "0.0.0.0"), self.port(9001, "")]},
                "mysql": {"ports": [self.port(3306, "::")]},
                "mysql-app-init": {},
                "api-server": {"ports": [self.port(8080, None)]},
                "popup-trend-collector": {},
            }
        }

        errors = security.production_compose_errors(config)

        self.assertEqual(4, len(errors))
        self.assertTrue(all("127.0.0.1" in error for error in errors))

    def test_rejects_missing_required_service_or_port(self) -> None:
        config = {
            "services": {
                "minio": {"ports": [self.port(9000)]},
                "mysql": {"ports": [self.port(3306)]},
                "api-server": {"ports": []},
            }
        }

        errors = security.production_compose_errors(config)

        self.assertTrue(any("minio:9001" in error for error in errors))
        self.assertTrue(any("api-server:8080" in error for error in errors))
        self.assertTrue(any("popup-trend-collector" in error for error in errors))

    def test_accepts_separate_root_and_runtime_credentials(self) -> None:
        self.assertEqual(
            [],
            security.production_credential_errors(self.secure_config()),
        )

    def test_rejects_reused_root_credentials_and_root_app_user(self) -> None:
        config = self.secure_config()
        services = config["services"]
        services["minio"]["environment"]["MINIO_ROOT_USER"] = "app-minio"
        services["minio"]["environment"]["MINIO_ROOT_PASSWORD"] = "app-secret"
        services["minio-init"]["environment"]["MINIO_ROOT_USER"] = "app-minio"
        services["minio-init"]["environment"]["MINIO_ROOT_PASSWORD"] = "app-secret"
        services["mysql"]["environment"]["MYSQL_ROOT_PASSWORD"] = "app-db-secret-123456"
        services["mysql"]["environment"]["MYSQL_USER"] = "root"
        services["api-server"]["environment"]["MYSQL_USERNAME"] = "root"

        errors = security.production_credential_errors(config)

        self.assertTrue(any("MinIO root user" in error for error in errors))
        self.assertTrue(any("MinIO root password" in error for error in errors))
        self.assertTrue(any("MySQL root password" in error for error in errors))
        self.assertTrue(any("must not be root" in error for error in errors))

    def test_rejects_privileged_credentials_in_api_runtime(self) -> None:
        config = self.secure_config()
        config["services"]["api-server"]["environment"]["MYSQL_ROOT_PASSWORD"] = "root-db-secret"

        errors = security.production_credential_errors(config)

        self.assertTrue(any("must not receive privileged credential" in error for error in errors))

    def test_rejects_unsafe_mysql_runtime_identity(self) -> None:
        config = self.secure_config()
        config["services"]["mysql"]["environment"]["MYSQL_USER"] = "app-user"
        config["services"]["mysql"]["environment"]["MYSQL_PASSWORD"] = "short secret"

        errors = security.production_credential_errors(config)

        self.assertTrue(any("app user must contain" in error for error in errors))
        self.assertTrue(any("at least 16" in error for error in errors))

    def test_production_overlay_requires_distinct_secret_inputs(self) -> None:
        content = security.PRODUCTION_COMPOSE_PATH.read_text(encoding="utf-8")

        self.assertEqual([], security.production_overlay_contract_errors(content))

    def test_minio_policy_is_bucket_scoped_and_runtime_only(self) -> None:
        policy = json.loads(security.MINIO_POLICY_PATH.read_text(encoding="utf-8"))

        self.assertEqual([], security.minio_policy_errors(policy))

    def test_rejects_broad_minio_policy(self) -> None:
        policy = {
            "Statement": [
                {
                    "Effect": "Allow",
                    "Action": ["s3:*"],
                    "Resource": ["arn:aws:s3:::*"],
                }
            ]
        }

        errors = security.minio_policy_errors(policy)

        self.assertTrue(any("actions" in error for error in errors))
        self.assertTrue(any("resources" in error for error in errors))

    def test_deployment_database_gate_requires_backup_schema_and_migrations_in_order(self) -> None:
        workflow = security.DEPLOY_WORKFLOW_PATH.read_text(encoding="utf-8")
        schema = security.SCHEMA_PATH.read_text(encoding="utf-8")

        self.assertEqual([], security.deployment_database_errors(workflow, schema))

    def test_deployment_database_gate_rejects_schema_before_backup(self) -> None:
        workflow = "\n".join((
            "SOURCE_SHA: ${{ github.sha }}",
            'git checkout --detach "$SOURCE_SHA"',
            'test "$(git rev-parse HEAD)" = "$SOURCE_SHA"',
            "backend/api-server/src/main/resources/schema.sql",
            'gzip -t "$backup_path"',
            "backend/api-server/src/main/resources/db/manual/20260710_launch_safety.sql",
            "backend/api-server/src/main/resources/db/manual/20260711_apple_token_storage.sql",
            "backend/api-server/src/main/resources/db/manual/20260711_ugc_publication_safety.sql",
            "backend/api-server/src/main/resources/db/manual/20260712_support_request_retention.sql",
            "deploy_api() {",
        ))
        schema = "\n".join(
            f"CREATE TABLE IF NOT EXISTS `{table}` ();"
            for table in ("users", "zone", "zone_review")
        )

        errors = security.deployment_database_errors(workflow, schema)

        self.assertTrue(any("out of order" in error for error in errors))

    @staticmethod
    def port(target: int, host_ip: str | None = "127.0.0.1") -> dict[str, object]:
        return {
            "target": target,
            "published": str(target),
            "protocol": "tcp",
            "host_ip": host_ip,
        }

    def secure_config(self) -> dict[str, object]:
        return {
            "services": {
                "minio": {
                    "environment": {
                        "MINIO_ROOT_USER": "root-minio",
                        "MINIO_ROOT_PASSWORD": "root-secret",
                    }
                },
                "minio-init": {
                    "entrypoint": [
                        "/bin/sh",
                        "-ec",
                        "mc anonymous set none; mc admin user add; "
                        "mc admin policy create; mc admin policy attach",
                    ],
                    "environment": {
                        "MINIO_ROOT_USER": "root-minio",
                        "MINIO_ROOT_PASSWORD": "root-secret",
                        "AWS_ACCESS_KEY_ID": "app-minio",
                        "AWS_SECRET_ACCESS_KEY": "app-secret",
                        "AWS_S3_BUCKET": "app-bucket",
                    },
                    "volumes": [
                        {
                            "target": "/config/nugulmap-app-policy.json",
                            "read_only": True,
                        }
                    ],
                },
                "mysql": {
                    "environment": {
                        "MYSQL_ROOT_PASSWORD": "root-db-secret",
                        "MYSQL_DATABASE": "app_db",
                        "MYSQL_USER": "app_user",
                        "MYSQL_PASSWORD": "app-db-secret-123456",
                    }
                },
                "mysql-app-init": {
                    "entrypoint": [
                        "/bin/sh",
                        "-ec",
                        "MYSQL_ROOT_PASSWORD must differ from MYSQL_PASSWORD; "
                        "MYSQL_USERNAME must be a non-root; "
                        "REVOKE ALL PRIVILEGES, GRANT OPTION; "
                        "GRANT SELECT, INSERT, UPDATE, DELETE",
                    ],
                    "environment": {
                        "MYSQL_ROOT_PASSWORD": "root-db-secret",
                        "MYSQL_DATABASE": "app_db",
                        "MYSQL_USERNAME": "app_user",
                        "MYSQL_PASSWORD": "app-db-secret-123456",
                    },
                },
                "api-server": {
                    "depends_on": {
                        "mysql-app-init": {
                            "condition": "service_completed_successfully",
                        }
                    },
                    "environment": {
                        "MYSQL_DATABASE": "app_db",
                        "MYSQL_USERNAME": "app_user",
                        "MYSQL_PASSWORD": "app-db-secret-123456",
                        "AWS_ACCESS_KEY_ID": "app-minio",
                        "AWS_SECRET_ACCESS_KEY": "app-secret",
                        "AWS_S3_BUCKET": "app-bucket",
                    }
                },
            }
        }


if __name__ == "__main__":
    unittest.main()
