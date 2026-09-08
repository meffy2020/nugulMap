from __future__ import annotations

import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
FRONTEND_WORKFLOW = REPO_ROOT / ".github/workflows/frontend.yml"
PUBLIC_CONTRACT_WORKFLOW = REPO_ROOT / ".github/workflows/public-contract.yml"
PRODUCTION_COMPOSE = REPO_ROOT / "docker-compose.production.yml"


class FrontendDeploymentWorkflowContractTest(unittest.TestCase):
    def test_vercel_owned_frontend_is_not_added_to_api_compose(self) -> None:
        compose = PRODUCTION_COMPOSE.read_text(encoding="utf-8")

        self.assertNotIn("frontend:", compose)
        self.assertNotIn("FRONTEND_IMAGE", compose)

    def test_frontend_workflow_builds_every_public_policy_route_without_deploying(self) -> None:
        workflow = FRONTEND_WORKFLOW.read_text(encoding="utf-8")

        self.assertIn("existing Vercel Git integration", workflow)
        for route in ("privacy", "account-deletion", "connected-accounts", "support", "terms"):
            self.assertIn(f".next/server/app/{route}.html", workflow)
        self.assertNotIn("docker compose", workflow)
        self.assertNotIn("vercel deploy", workflow.lower())

    def test_public_contract_runs_after_frontend_verification_with_propagation_retry(self) -> None:
        workflow = PUBLIC_CONTRACT_WORKFLOW.read_text(encoding="utf-8")

        self.assertIn("- Verify Frontend", workflow)
        self.assertIn("github.event.workflow_run.head_branch == 'main'", workflow)
        self.assertIn("python3 scripts/check-public-launch-runtime.py", workflow)
        self.assertIn("retrying in 30 seconds", workflow)


if __name__ == "__main__":
    unittest.main()
