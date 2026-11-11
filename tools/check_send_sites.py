#!/usr/bin/env python3
"""Standalone guard for the single-send-site rule (BUILD-SPEC section 1, rule 1).

Stdlib only, no Android SDK. Mirrors SingleSendSiteTest so the rule can be checked without Gradle.
Scans production Kotlin source only (never test source sets) and asserts:
  * sendReply, markReplySent and sendConfirmedReply each have exactly one call site
    outside their definition (comments and string literals are ignored);
  * the ':reply' endpoint suffix appears only in PlayReviewsApiClient.kt.

Usage: python3 tools/check_send_sites.py [--root DIR]
Exit status: 0 pass, 1 rule violated, 2 no production source found.
"""
import argparse
import os
import re
import sys

GUARDED = ("sendReply", "markReplySent", "sendConfirmedReply")
ENDPOINT = ":reply"
ENDPOINT_FILE = "PlayReviewsApiClient.kt"
# Production source sets: app/src/main today; core/ui/desktop *Main later.
SOURCE_SET = re.compile(r"(^|/)src/(main|[A-Za-z0-9]+Main)$")


def strip(src, keep_strings):
    """Blank out comments (and string contents unless keep_strings), keeping line numbers."""
    out, i, n = [], 0, len(src)
    while i < n:
        c = src[i]
        two = src[i:i + 2]
        if two == "//":
            while i < n and src[i] != "\n":
                i += 1
        elif two == "/*":
            depth = 0  # Kotlin block comments nest
            while i < n:
                if src[i:i + 2] == "/*":
                    depth += 1
                    i += 2
                elif src[i:i + 2] == "*/":
                    depth -= 1
                    i += 2
                    if depth == 0:
                        break
                else:
                    if src[i] == "\n":
                        out.append("\n")
                    i += 1
        elif src[i:i + 3] == '"""':
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            body = src[i:j]
            out.append(body if keep_strings else "".join("\n" if ch == "\n" else " " for ch in body))
            i = j
        elif c in "\"'":
            j = i + 1
            while j < n and src[j] != c and src[j] != "\n":
                j += 2 if src[j] == "\\" else 1
            j = min(j + 1, n)
            body = src[i:j]
            out.append(body if keep_strings else c + " " * (len(body) - 2) + c)
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


def production_files(root):
    for dirpath, dirnames, files in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in (".git", "build", ".gradle", "node_modules", "shared-libraries")]
        rel = os.path.relpath(dirpath, root).replace(os.sep, "/")
        # Inside a production source set: path has a ".../src/<set>" ancestor that is a Main set.
        parts = rel.split("/")
        in_prod = any(
            SOURCE_SET.search("/".join(parts[: k + 1])) for k in range(len(parts))
        )
        if not in_prod:
            continue
        for f in files:
            if f.endswith(".kt"):
                yield os.path.join(dirpath, f)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--root", default=".")
    root = ap.parse_args().root

    sites = {name: [] for name in GUARDED}
    endpoint_files = []
    scanned = 0
    for path in production_files(root):
        scanned += 1
        rel = os.path.relpath(path, root).replace(os.sep, "/")
        with open(path, encoding="utf-8") as fh:
            raw = fh.read()
        code = strip(raw, keep_strings=False)
        for lineno, line in enumerate(code.split("\n"), 1):
            for name in GUARDED:
                for m in re.finditer(r"\b%s\s*\(" % name, line):
                    if re.search(r"\bfun\s+(?:[\w.<>?]+\.)?$", line[: m.start()]):
                        continue  # definition
                    sites[name].append("%s:%d" % (rel, lineno))
        if ENDPOINT in strip(raw, keep_strings=True) and os.path.basename(path) != ENDPOINT_FILE:
            endpoint_files.append(rel)

    if scanned == 0:
        print("FAIL: no production Kotlin source found under %s" % root)
        return 2

    bad = False
    for name in GUARDED:
        ok = len(sites[name]) == 1
        bad |= not ok
        print("%s %s: %d call site(s) %s" % ("ok  " if ok else "FAIL", name, len(sites[name]), ", ".join(sites[name])))
    # ':reply' must appear in the endpoint file (at least once) and nowhere else.
    if endpoint_files:
        bad = True
        print("FAIL '%s' outside %s: %s" % (ENDPOINT, ENDPOINT_FILE, ", ".join(endpoint_files)))
    else:
        print("ok   '%s' only in %s" % (ENDPOINT, ENDPOINT_FILE))
    print("scanned %d production file(s)" % scanned)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
