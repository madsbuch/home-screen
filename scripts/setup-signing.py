#!/usr/bin/env python3
"""Create/reuse this app's signing key and upload it as encrypted GitHub secrets."""

import argparse
import base64
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', required=True, help='GitHub OWNER/REPO receiving signing secrets')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    directory = root / '.signing'
    directory.mkdir(mode=0o700, exist_ok=True)
    os.chmod(directory, 0o700)
    config_path = directory / 'signing.json'
    keystore = directory / 'quiet-home.jks'

    if config_path.exists():
        config = json.loads(config_path.read_text())
        if not keystore.is_file():
            raise SystemExit('Signing metadata exists but its key is missing. Restore the key from backup; do not replace it.')
    else:
        if keystore.exists():
            raise SystemExit('A signing key already exists. Restore its signing.json metadata before continuing.')
        java_home = os.environ.get('JAVA_HOME')
        keytool = str(Path(java_home) / 'bin/keytool') if java_home else shutil.which('keytool')
        if not keytool:
            raise SystemExit('Set JAVA_HOME to a JDK before running this script.')
        config = {'alias': 'quiet-home', 'password': secrets.token_hex(32)}
        env = dict(os.environ, QUIET_HOME_SIGNING_PASSWORD=config['password'])
        subprocess.run([
            keytool, '-genkeypair', '-keystore', str(keystore), '-storetype', 'JKS',
            '-alias', config['alias'], '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
            '-storepass:env', 'QUIET_HOME_SIGNING_PASSWORD', '-keypass:env', 'QUIET_HOME_SIGNING_PASSWORD',
            '-dname', 'CN=Quiet Home, O=Quiet Home',
        ], env=env, check=True)
        descriptor = os.open(config_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(descriptor, 'w') as output:
            json.dump(config, output)
        os.chmod(keystore, 0o600)

    # Send secret values on stdin, never in command-line arguments or logs.
    values = {
        'ANDROID_KEY_BASE64': base64.b64encode(keystore.read_bytes()).decode('ascii'),
        'ANDROID_KEY_ALIAS': config['alias'],
        'ANDROID_KEY_PASSWORD': config['password'],
        'ANDROID_KEYSTORE_PASSWORD': config['password'],
    }
    for name, value in values.items():
        subprocess.run(['gh', 'secret', 'set', name, '--repo', args.repo], input=value, text=True, check=True)
        print(f'Configured {name}')
    print('Signing secrets configured. Back up the ignored .signing directory securely; keep the same key for updates.')


if __name__ == '__main__':
    main()
