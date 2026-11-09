#!/usr/bin/env python3
"""
Professional Zero-Emoji Release Notes & APK Checksum Generator
Mengekstrak riwayat commit git sejak tag rilis sebelumnya, mengelompokkannya secara
struktural (Fitur Baru, Perbaikan Bug, Arsitektur & Optimasi), dan menyertakan
tabel ukuran serta hash SHA-256 untuk setiap aset APK (arm64-v8a & armeabi-v7a).
"""

import os
import re
import sys
import glob
import hashlib
import argparse
import subprocess


# Regex untuk menghapus seluruh karakter emoji Unicode jika ada di pesan commit
EMOJI_PATTERN = re.compile(
    "["
    "\U0001F600-\U0001F64F"
    "\U0001F300-\U0001F5FF"
    "\U0001F680-\U0001F6FF"
    "\U0001F700-\U0001F77F"
    "\U0001F780-\U0001F7FF"
    "\U0001F800-\U0001F8FF"
    "\U0001F900-\U0001F9FF"
    "\U0001FA00-\U0001FA6F"
    "\U0001FA70-\U0001FAFF"
    "\U00002702-\U000027B0"
    "\U000024C2-\U0001F251"
    "]+",
    flags=re.UNICODE,
)


def strip_emojis(text: str) -> str:
    return EMOJI_PATTERN.sub("", text).strip()


def run_git(args):
    try:
        res = subprocess.run(
            ["git"] + args,
            capture_output=True,
            text=True,
            check=True,
        )
        return res.stdout.strip()
    except Exception:
        return ""


def get_previous_tag(current_tag: str) -> str:
    tags_raw = run_git(["tag", "--sort=-creatordate"])
    if not tags_raw:
        return ""
    tags = [t.strip() for t in tags_raw.splitlines() if t.strip()]
    for t in tags:
        if t != current_tag:
            return t
    return ""


def get_commits(prev_tag: str):
    rev_range = f"{prev_tag}..HEAD" if prev_tag else "HEAD"
    out = run_git(["log", rev_range, "--no-merges", "--pretty=format:%h|%s"])
    if not out:
        return []
    commits = []
    for line in out.splitlines():
        if "|" in line:
            sha, subj = line.split("|", 1)
            commits.append((sha.strip(), strip_emojis(subj.strip())))
    return commits


def categorize_commits(commits):
    features = []
    fixes = []
    improvements = []
    others = []

    for sha, subject in commits:
        # Bersihkan prefix Release vX.Y.Z - jika ada
        clean_subj = re.sub(r"^Release\s+v[0-9.]+\s*[-:]\s*", "", subject, flags=re.IGNORECASE)
        lower = clean_subj.lower()

        # Lewati commit administratif murni jika sudah ada commit teknis lainnya
        if lower.startswith("docs(state)") or lower.startswith("chore(git)"):
            others.append((sha, clean_subj))
            continue

        if lower.startswith("feat") or "add " in lower or "redesign" in lower or "implement" in lower:
            clean_subj = re.sub(r"^feat(\([^)]+\))?:\s*", "", clean_subj, flags=re.IGNORECASE)
            features.append((sha, clean_subj))
        elif lower.startswith("fix") or "bug" in lower or "crash" in lower or "resolve" in lower:
            clean_subj = re.sub(r"^fix(\([^)]+\))?:\s*", "", clean_subj, flags=re.IGNORECASE)
            fixes.append((sha, clean_subj))
        elif lower.startswith(("perf", "refactor", "arch", "build", "ci")):
            clean_subj = re.sub(r"^(perf|refactor|arch|build|ci)(\([^)]+\))?:\s*", "", clean_subj, flags=re.IGNORECASE)
            improvements.append((sha, clean_subj))
        else:
            improvements.append((sha, clean_subj))

    if not features and not fixes and not improvements:
        improvements.extend(others)

    return features, fixes, improvements


def compute_sha256(filepath: str) -> str:
    h = hashlib.sha256()
    with open(filepath, "rb") as fp:
        while True:
            chunk = fp.read(65536)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def format_size(size_bytes: int) -> str:
    return f"{size_bytes / (1024.0 * 1024.0):.2f} MB"


def detect_abi_label(filename: str) -> str:
    lower = filename.lower()
    if "arm64-v8a" in lower:
        return "ARM64 (64-bit Modern Android)"
    if "armeabi-v7a" in lower:
        return "ARM32 (32-bit Legacy Android)"
    if "universal" in lower:
        return "Universal (Multi-ABI)"
    return "Standard Release APK"


def generate_markdown(tag: str, prev_tag: str, apk_dir: str, repo: str) -> str:
    commits = get_commits(prev_tag)
    features, fixes, improvements = categorize_commits(commits)

    lines = []
    lines.append(f"## Catatan Rilis {tag}")
    lines.append("")

    if features:
        lines.append("### Fitur & Pembaruan Antarmuka")
        for sha, text in features:
            lines.append(f"- {text} (`{sha}`)")
        lines.append("")

    if fixes:
        lines.append("### Perbaikan Bug & Stabilitas")
        for sha, text in fixes:
            lines.append(f"- {text} (`{sha}`)")
        lines.append("")

    if improvements:
        lines.append("### Arsitektur & Optimasi Sistem")
        for sha, text in improvements:
            lines.append(f"- {text} (`{sha}`)")
        lines.append("")

    # Tabel Aset APK & Checksum SHA-256
    apk_files = sorted(glob.glob(os.path.join(apk_dir, "*.apk")))
    if apk_files:
        lines.append("### Paket Instalasi Berdasarkan Arsitektur CPU (ABI)")
        lines.append("")
        lines.append("| Nama Berkas | Target Arsitektur | Ukuran | SHA-256 Checksum |")
        lines.append("| :--- | :--- | :--- | :--- |")
        for apk in apk_files:
            fname = os.path.basename(apk)
            size_str = format_size(os.path.getsize(apk))
            sha256 = compute_sha256(apk)
            abi_label = detect_abi_label(fname)
            lines.append(f"| `{fname}` | {abi_label} | {size_str} | `{sha256}` |")
        lines.append("")
        lines.append("> **Catatan In-App Updater:** Aplikasi secara otomatis mendeteksi instruksi CPU perangkat Anda (`Build.SUPPORTED_ABIS`) dan hanya mengunduh varian APK yang sesuai (`arm64-v8a` atau `armeabi-v7a`).")
        lines.append("")

    if repo and prev_tag:
        lines.append(f"**Riwayat Perubahan Lengkap:** https://github.com/{repo}/compare/{prev_tag}...{tag}")

    return "\n".join(lines).strip() + "\n"


def main():
    parser = argparse.ArgumentParser(description="Generate Zero-Emoji Release Notes")
    parser.add_argument("--tag", required=True, help="Tag rilis saat ini (mis. v1.2.0)")
    parser.add_argument("--apk-dir", default="app/build/outputs/apk/release", help="Direktori output APK")
    parser.add_argument("--repo", default=os.environ.get("GITHUB_REPOSITORY", ""), help="Nama repo owner/name")
    parser.add_argument("--output", default="RELEASE_NOTES.md", help="Path berkas output markdown")
    args = parser.parse_args()

    prev_tag = get_previous_tag(args.tag)
    markdown = generate_markdown(args.tag, prev_tag, args.apk_dir, args.repo)

    with open(args.output, "w", encoding="utf-8") as fp:
        fp.write(markdown)

    print(f"Release notes berhasil dibuat di: {args.output}")


if __name__ == "__main__":
    main()
