"""Make the monitor's admin password hash (app/admin.py · 10-07).

    py backend/scripts/admin_password.py

Asks for the password twice without showing it, then prints the two lines for the server's .env:

    ADMIN_USER=...
    ADMIN_PASSWORD_HASH=pbkdf2_sha256$...

The password itself is never written anywhere. Restart the backend container after editing .env.
"""
import getpass
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.admin import hash_password  # noqa: E402


def main() -> None:
    user = input("admin user name: ").strip()
    if not user:
        sys.exit("a user name is needed")
    pw = getpass.getpass("password (12+ characters): ")
    if len(pw) < 12:
        sys.exit("use at least 12 characters")
    if getpass.getpass("again: ") != pw:
        sys.exit("the two did not match")
    print()
    print(f"ADMIN_USER={user}")
    # no quotes: the server reads .env through `docker run --env-file`, which keeps quotes as part of the value
    print(f"ADMIN_PASSWORD_HASH={hash_password(pw)}")


if __name__ == "__main__":
    main()
