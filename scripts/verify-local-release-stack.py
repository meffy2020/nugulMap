#!/usr/bin/env python3
"""Exercise the release jar against disposable, loopback-only MySQL 8.

Requires Docker, Java 21, openssl and a freshly built bootJar. Never connects to
an existing database or accepts a remote API URL. All accounts and keys are fixtures.
"""
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import shutil
import signal
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'backend/api-server/src/main/resources'
JAR = ROOT / 'backend/api-server/build/libs/neogul-map-0.0.1-SNAPSHOT.jar'


def run(args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, **kwargs).stdout.strip()


def main():
    def terminated(_signum, _frame):
        raise SystemExit('Release verification interrupted; cleaning up owned resources.')

    signal.signal(signal.SIGTERM, terminated)
    if not JAR.is_file():
        raise SystemExit('Build backend bootJar with Java 21 first.')
    run(['docker', 'image', 'inspect', 'mysql:8.0'])  # No implicit image pull.
    java_home = os.environ.get('JAVA_HOME')
    if not java_home and shutil.which('/usr/libexec/java_home'):
        java_home = run(['/usr/libexec/java_home', '-v', '21'])
    java = str(Path(java_home) / 'bin/java') if java_home else 'java'
    name = 'nugul-release-check-' + secrets.token_hex(6)
    password = secrets.token_hex(24)
    secret = secrets.token_bytes(32)
    operator = secrets.token_hex(24)
    proc = None
    checks = []
    scratch = Path(tempfile.mkdtemp(prefix='nugul-release-check-'))
    print('Private local logs:', scratch, flush=True)

    def check(label, condition):
        if not condition:
            raise AssertionError(label)
        checks.append(label)
        print('PASS:', label, flush=True)

    def sql(statement):
        env = {**os.environ, 'MYSQL_PWD': password}
        return run(['docker', 'exec', '-i', '-e', 'MYSQL_PWD', name,
                    'mysql', '-uroot', '--batch', '--skip-column-names',
                    '--default-character-set=utf8mb4', 'release_check'], input=statement, env=env)

    try:
        envfile = scratch / 'mysql.env'
        envfile.write_text(f'MYSQL_ROOT_PASSWORD={password}\nMYSQL_DATABASE=release_check\n')
        envfile.chmod(0o600)
        run(['docker', 'run', '--detach', '--rm', '--name', name,
             '--label', 'nugulmap.disposable-release-check=true',
             '--env-file', str(envfile), '-p', '127.0.0.1::3306',
             '--tmpfs', '/var/lib/mysql:rw,size=768m', 'mysql:8.0'])
        envfile.unlink()
        for _ in range(90):
            try:
                if sql('SELECT 1;') == '1':
                    break
            except subprocess.CalledProcessError:
                pass
            time.sleep(1)
        else:
            raise RuntimeError('Disposable MySQL did not become ready.')
        port = run(['docker', 'port', name, '3306/tcp']).rsplit(':', 1)[1]
        migrations = sorted((RES / 'db/manual').glob('*.sql'))
        for _ in range(2):
            sql((RES / 'schema.sql').read_text())
            for migration in migrations:
                sql(migration.read_text())
        check('MySQL 8 canonical schema and all ordered migrations run twice', True)
        sql((RES / 'data.sql').read_text())
        check('Explicit demo fixture contains ten zones', sql('SELECT COUNT(*) FROM zone;') == '10')
        quarantine = (RES / 'db/manual/20260907_quarantine_demo_zones.sql').read_text()
        sql(quarantine)
        sql(quarantine)
        check('Demo quarantine idempotent; ten rows retained and unpublished',
              sql("SELECT COUNT(*), SUM(publication_status='PENDING') FROM zone;") == '10\t10')
        sql("INSERT INTO zone(region,address,description,latitude,longitude,publication_status) "
            "VALUES('LOCAL FIXTURE','LOCAL RELEASE FIXTURE','Synthetic local verification only',37.57,126.98,'PUBLISHED');")
        zone_id = int(sql("SELECT id FROM zone WHERE address='LOCAL RELEASE FIXTURE';"))
        key = scratch / 'apple-fixture.pem'
        run(['openssl', 'genpkey', '-algorithm', 'EC', '-pkeyopt', 'ec_paramgen_curve:P-256', '-out', str(key)])
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            api_port = sock.getsockname()[1]
        # Inherit only host tooling variables, never provider/storage/Spring overrides.
        env = {**{k: v for k, v in os.environ.items() if k in ('PATH', 'HOME', 'TMPDIR', 'LANG', 'JAVA_HOME')},
               'SPRING_PROFILES_ACTIVE': 'mysql,prod', 'SERVER_ADDRESS': '127.0.0.1',
               'SERVER_PORT': str(api_port), 'MYSQL_HOST': '127.0.0.1', 'MYSQL_PORT': port,
               'MYSQL_DATABASE': 'release_check', 'MYSQL_USERNAME': 'root', 'MYSQL_PASSWORD': password,
               'JWT_SECRET': base64.b64encode(secret).decode(), 'MODERATION_OPERATOR_KEY': operator,
               'MODERATION_CONTACT_EMAIL': 'release-check@example.invalid',
               'APPLE_CLIENT_ID': 'local.release.fixture', 'APPLE_TEAM_ID': 'LOCALCHECK', 'APPLE_KEY_ID': 'LOCALCHECK',
               'APPLE_PRIVATE_KEY_BASE64': base64.b64encode(key.read_bytes()).decode(),
               'APPLE_TOKEN_ENCRYPTION_KEY': base64.b64encode(secrets.token_bytes(32)).decode(),
               'APP_UPLOAD_DIR': str(scratch / 'uploads'), 'APP_STORAGE_TYPE': 'local',
               'APP_DISCOVERY_ENABLED': 'false',
               'EXTERNAL_INSIGHTS_HOTPLACE_WARMUP_ENABLED': 'false',
               'EXTERNAL_INSIGHTS_EVENT_WARMUP_ENABLED': 'false'}
        for provider in ('GOOGLE', 'KAKAO', 'NAVER'):
            env[provider + '_CLIENT_ID'] = 'local-release-fixture'
            env[provider + '_CLIENT_SECRET'] = 'local-release-fixture'
        log = (scratch / 'api.log').open('w')
        proc = subprocess.Popen([java, '-jar', str(JAR)], cwd=scratch, env=env, stdout=log, stderr=subprocess.STDOUT)
        base = f'http://127.0.0.1:{api_port}/api'

        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl):
                return None

        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

        def request(path, method='GET', body=None, token=None, operator_key=None, multipart=False):
            headers = {'Content-Type': 'application/json'}
            if token:
                headers['Authorization'] = 'Bearer ' + token
            if operator_key:
                headers['X-Nugul-Operator-Key'] = operator_key
            data = None if body is None else json.dumps(body).encode()
            if multipart:
                boundary = 'release-check-' + secrets.token_hex(12)
                headers['Content-Type'] = 'multipart/form-data; boundary=' + boundary
                data = (f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\n'
                        'Content-Type: application/json\r\n\r\n' + json.dumps(body)
                        + f'\r\n--{boundary}--\r\n').encode()
            req = urllib.request.Request(base + path, method=method, headers=headers, data=data)
            try:
                response = opener.open(req, timeout=10)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                raw = response.read().decode()
                return response.status, json.loads(raw) if raw else None

        for _ in range(90):
            if proc.poll() is not None:
                raise RuntimeError('Production profile failed; inspect private api.log.')
            try:
                if request('/actuator/health')[0] == 200:
                    break
            except (OSError, ValueError):
                pass
            time.sleep(1)
        else:
            raise RuntimeError('Production profile health timeout.')
        check('Production profile boots on real MySQL and health is UP', request('/actuator/health')[1]['status'] == 'UP')
        for path in ('/insights/status', '/recommendations/places', '/location-letters'):
            check('Retired endpoint absent: ' + path, request(path)[0] == 404)
        status, zones = request('/zones/bounds?minLat=37&maxLat=38&minLng=126&maxLng=128')
        check('Public bounds returns exactly the published fixture and no demos',
              status == 200 and zones['data']['count'] == 1
              and [z['id'] for z in zones['data']['zones']] == [zone_id]
              and 'test1@example.com' not in json.dumps(zones))
        check('Quarantined detail unavailable', request('/zones/1')[0] == 404)
        status, support = request('/public/support/requests', 'POST',
                                  {'category': 'OTHER', 'email': 'fixture@example.invalid', 'message': 'Local integration verification', 'website': ''})
        check('Public support request accepted', status in (200, 201, 202))
        check('Wrong operator key denied', request('/operator/support/requests', operator_key='wrong')[0] in (401, 403))
        check('Operator can read support queue', request('/operator/support/requests', operator_key=operator)[0] == 200)
        support_id = int(sql('SELECT id FROM support_request ORDER BY id DESC LIMIT 1;'))
        check('Operator resolves support request', request(f'/operator/support/requests/{support_id}', 'PATCH', {'status': 'RESOLVED'}, operator_key=operator)[0] == 200)

        def jwt(email, user_id):
            def encode(value):
                return base64.urlsafe_b64encode(value).rstrip(b'=').decode()
            now = int(time.time())
            head = encode(json.dumps({'alg': 'HS256', 'typ': 'JWT'}).encode())
            payload = encode(json.dumps({'sub': email, 'userId': user_id, 'tokenType': 'access', 'iat': now, 'exp': now + 300}).encode())
            signed = head + '.' + payload
            return signed + '.' + encode(hmac.new(secret, signed.encode(), hashlib.sha256).digest())

        first = int(sql("SELECT id FROM users WHERE email='test1@example.com';"))
        second = int(sql("SELECT id FROM users WHERE email='test2@example.com';"))
        token1, token2 = jwt('test1@example.com', first), jwt('test2@example.com', second)
        check('Bearer token authenticates local fixture', request(f'/users/{first}', token=token1)[0] == 200)
        check('Other account access denied', request(f'/users/{second}', token=token1)[0] == 403)
        status, submitted = request('/zones', 'POST', {
            'region': 'LOCAL FIXTURE', 'address': 'LOCAL USER SUBMISSION FIXTURE',
            'description': 'Synthetic local submission', 'latitude': 37.57,
            'longitude': 126.98, 'type': '흡연구역', 'subtype': '실외'}, token=token1, multipart=True)
        check('Authenticated multipart zone submission accepted', status == 201)
        submitted_id = submitted['data']['zone']['id']
        check('New submission is pending and unavailable publicly',
              sql(f'SELECT publication_status FROM zone WHERE id={submitted_id};') == 'PENDING'
              and request(f'/zones/{submitted_id}')[0] == 404)
        status, mine = request('/zones/my', token=token1)
        check('Submitter can see pending submission in own list', status == 200
              and submitted_id in [z['id'] for z in mine['data']['zones']])
        decision_path = f'/operator/moderation/reports/zone-submissions/{submitted_id}'
        check('Untrusted operator cannot publish submission', request(decision_path, 'PATCH',
              {'action': 'PUBLISH'}, operator_key='wrong')[0] in (401, 403))
        check('Authorized operator publishes submission', request(decision_path, 'PATCH',
              {'action': 'PUBLISH'}, operator_key=operator)[0] == 200)
        status, published = request(f'/zones/{submitted_id}')
        check('Approved submission visible without creator email', status == 200
              and 'test1@example.com' not in json.dumps(published))
        check('Authenticated review creation', request(f'/zones/{zone_id}/reviews', 'POST', {'content': 'Local release fixture review'}, token=token1)[0] == 201)
        review_id = int(sql(f'SELECT id FROM zone_review WHERE zone_id={zone_id} ORDER BY id DESC LIMIT 1;'))
        status, visible = request(f'/zones/{zone_id}/reviews', token=token2)
        check('Other user sees the review before blocking', status == 200
              and review_id in [r['id'] for r in visible['data']['reviews']])
        check('Review report accepted', request(f'/zones/{zone_id}/reviews/{review_id}/reports', 'POST', {'reason': 'SPAM', 'details': 'Local check'}, token=token2)[0] == 201)
        check('User block accepted', request(f'/users/{first}/block', 'POST', token=token2)[0] == 200)
        status, hidden = request(f'/zones/{zone_id}/reviews', token=token2)
        check('Blocked author review hidden', status == 200
              and review_id not in [r['id'] for r in hidden['data']['reviews']])
        check('Self account deletion succeeds', request(f'/users/{first}', 'DELETE', token=token1)[0] == 200)
        check('Account and authored reviews removed', sql(f'SELECT COUNT(*) FROM users WHERE id={first}; SELECT COUNT(*) FROM zone_review WHERE author_id={first};') == '0\n0')
        check('User-owned submission removed as promised by account-deletion policy',
              sql(f'SELECT COUNT(*) FROM zone WHERE id={submitted_id};') == '0'
              and request(f'/zones/{submitted_id}')[0] == 404)
        check('Deleted account token rejected', request(f'/users/{first}', token=token1)[0] == 401)
        proc.terminate()
        proc.wait(timeout=20)
        # Production must reject schema drift, even if a legacy MySQL override
        # would otherwise disable Hibernate validation.
        sql('ALTER TABLE users DROP COLUMN nickname;')
        env['MYSQL_DDL_AUTO'] = 'none'
        negative_log = scratch / 'schema-drift.log'
        with negative_log.open('w') as negative:
            proc = subprocess.Popen([java, '-jar', str(JAR)], cwd=scratch, env=env,
                                    stdout=negative, stderr=subprocess.STDOUT)
            try:
                code = proc.wait(timeout=60)
            except subprocess.TimeoutExpired:
                raise AssertionError('Production did not reject a missing required column.')
        check('Production rejects missing required column despite MYSQL_DDL_AUTO=none',
              code != 0 and 'missing column [nickname]' in negative_log.read_text())
        print(json.dumps({'result': 'PASS', 'checks': len(checks), 'database': 'disposable MySQL 8',
                          'scope': 'loopback fixtures only; no production writes or real OAuth'}, indent=2), flush=True)
    finally:
        if proc:
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait()
        cleanup = subprocess.run(['docker', 'rm', '-f', name], capture_output=True, text=True)
        if cleanup.returncode and 'No such container' not in cleanup.stderr:
            raise RuntimeError('Could not remove owned disposable container: ' + name)
        for filename in ('mysql.env', 'apple-fixture.pem'):
            (scratch / filename).unlink(missing_ok=True)


if __name__ == '__main__':
    main()
