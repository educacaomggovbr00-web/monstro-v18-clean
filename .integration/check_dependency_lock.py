"""Reject any modification to previously pinned artifact checksums."""
import sys
import xml.etree.ElementTree as ET

def checksums(path):
    root = ET.parse(path).getroot()
    ns = {"v": "https://schema.gradle.org/dependency-verification"}
    result = {}
    for component in root.findall("v:components/v:component", ns):
        for artifact in component.findall("v:artifact", ns):
            key = (component.get("group"), component.get("name"), component.get("version"), artifact.get("name"))
            result[key] = {node.get("value") for node in artifact.findall("v:sha256", ns)}
    return result

before, after = map(checksums, sys.argv[1:])
changed = [key for key, values in before.items() if after.get(key) != values]
if changed:
    raise SystemExit("Existing checksum changed: " + repr(changed))
print(f"Existing {len(before)} artifact pins preserved; {len(after) - len(before)} artifacts registered.")
