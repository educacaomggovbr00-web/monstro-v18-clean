"""Migrate filesystem references and baseline class descriptors to Monstro."""
from pathlib import Path
import subprocess

changed = []
for name in subprocess.check_output(["git", "ls-files"], text=True).splitlines():
    if name.startswith(("legacy/", ".integration/")):
        continue
    path = Path(name)
    try:
        source = path.read_text(encoding="utf-8")
    except (UnicodeError, OSError):
        continue
    migrated = source.replace("com/novacut/editor", "com/monstro/v18").replace('/ "novacut" / "editor"', '/ "monstro" / "v18"')
    if migrated != source:
        path.write_text(migrated, encoding="utf-8")
        changed.append(name)
print(f"Migrated {len(changed)} filesystem references and baseline profile files.")
