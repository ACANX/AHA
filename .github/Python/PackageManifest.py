#!/usr/bin/env python3
"""包内清单 `AHA-PACKAGE.json` 与发布页校验和 `SHA256SUMS` 的唯一生成 / 校验实现（issue #51 §6）。

为什么要有这个脚本：`Docs/DevSpec/ReleaseProcess.md` 定了四种包形态，`BuildSpec.md` 定了四条出包线
（正式发版、dev 线 JVM 包、两条原生线）。清单与校验和要在**每条线**都产出，若各写一段 shell，
四处必然漂移——而漂移的表现是「某个通道的包永远校验不过」，静默且难查。所以规则只放这里一处，
四条线都调它，`bin/CheckScripts.py` 的 `check_release_channel_contract()` 钉住「四条线都调了」。

用法：

    # ① 打清单进包（包根，幂等：已有则替换）
    python3 .github/Python/PackageManifest.py generate --zip Dist/aha-desktop-0.1.3.00242-windows-amd64.zip \\
        --kind desktop-jvm --version 0.1.3.00242 --platform windows-amd64 --jdk 25

    # ② 反查：包内清单与包的真实内容逐项一致（大小 + sha256 + 文件集）
    python3 .github/Python/PackageManifest.py verify --zip Dist/…zip --kind desktop-jvm --version 0.1.3.00242

    # ③ 生成发布页资产 SHA256SUMS（每个 zip 一行，`<sha256>  <文件名>`）
    python3 .github/Python/PackageManifest.py sums --dir Dist --out Dist/SHA256SUMS

    python3 .github/Python/PackageManifest.py --self-test

口径（Java 侧的 `PackageManifest.java` 必须与之逐字对应）：

* 清单里的 `path` 是**包根相对路径**（`/` 分隔），`sha256` / `size` 指的是**解压后**的文件
  ——落地器就是这么核对的（`PackageManifest.java`）；
* 清单写在**包根**：`AHA-PACKAGE.json` 自身**不进** `files`（否则校验和自指）；
* `kind` 取值与 Java 的 `UpdateKind.of()` 一致：`desktop-jvm` / `desktop-native` / `cli-jvm` / `cli-native`；
  四种形态**都必须带** `--platform`（自 issue #269 起 CLI-JVM 也按平台拆名）；
* 打包垃圾（`__MACOSX/`、`.DS_Store`）与非法路径（绝对路径、`..`、反斜杠）一律**响亮失败**，
  而不是默默写进清单；
* 生成与校验都是确定性的（条目按 `path` 排序），同一份输入产出同一份 JSON，便于比对。

退出码：0 = 通过；1 = 生成 / 校验失败；2 = 用法错误。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path

#: 清单文件名（放包根）。
MANIFEST_NAME = "AHA-PACKAGE.json"

#: 发布页校验和文件名。
SUMS_NAME = "SHA256SUMS"

#: 形态 → 资产名是否带平台段（与 `UpdateKind` 一致）。
KIND_HAS_PLATFORM = {
    "desktop-jvm": True,
    "desktop-native": True,
    # issue #269：CLI-JVM 也带平台段（三平台三个名字，内容暂相同）。
    "cli-jvm": True,
    "cli-native": True,
}

#: 打包垃圾：出现即失败（说明打包环境脏了）。
JUNK_PREFIXES = ("__MACOSX/",)
JUNK_NAMES = (".DS_Store", "Thumbs.db")

#: 哈希分块大小。
CHUNK = 1 << 20


class ManifestError(Exception):
    """清单 / 校验和不合规（路径非法、内容不符、字段缺失）。"""


@dataclass(frozen=True)
class Entry:
    """包内一个文件（解压后的视角）。"""

    path: str
    sha256: str
    size: int

    def to_json(self) -> dict:
        return {"path": self.path, "sha256": self.sha256, "size": self.size}


def sha256_file(path: Path) -> str:
    """算整个文件的 SHA-256（发布页校验和用）。"""
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(CHUNK):
            digest.update(chunk)
    return digest.hexdigest()


def check_entry_name(name: str, where: str) -> None:
    """条目名必须是包根相对、`/` 分隔、不含 `..`、不是打包垃圾。"""
    if name.endswith("/"):
        raise ManifestError(f"{where}：条目 `{name}` 是目录，不该出现在清单里")
    if "\\" in name:
        raise ManifestError(f"{where}：条目 `{name}` 用了反斜杠（契约要求 `/`）")
    if name.startswith("/") or (len(name) > 1 and name[1] == ":"):
        raise ManifestError(f"{where}：条目 `{name}` 是绝对路径（契约要求相对路径）")
    if ".." in name.split("/"):
        raise ManifestError(f"{where}：条目 `{name}` 含 `..` 段（会解压到包外）")
    if name.startswith(JUNK_PREFIXES) or Path(name).name in JUNK_NAMES:
        raise ManifestError(f"{where}：条目 `{name}` 是打包垃圾，请先清掉再出包")


def read_entries(zip_path: Path, where: str = "包") -> list[Entry]:
    """读出包内所有文件的（相对路径、sha256、大小），跳过目录条目与清单自身。"""
    if not zip_path.is_file():
        raise ManifestError(f"{where}：文件不存在：{zip_path}")
    entries: list[Entry] = []
    with zipfile.ZipFile(zip_path) as archive:
        for info in archive.infolist():
            if info.is_dir() or info.filename == MANIFEST_NAME:
                continue
            check_entry_name(info.filename, where)
            digest = hashlib.sha256()
            with archive.open(info) as handle:
                while chunk := handle.read(CHUNK):
                    digest.update(chunk)
            entries.append(Entry(info.filename, digest.hexdigest(), info.file_size))
    entries.sort(key=lambda entry: entry.path)
    return entries


#: 原生形态：清单必须记录构建它所用的 GraalVM 精确版本（issue #269 §5）。
NATIVE_KINDS = frozenset({"desktop-native", "cli-native"})


def build_manifest(kind: str, version: str, platform: str | None, jdk: int,
                   entries: list[Entry], graalvm: str | None = None) -> dict:
    """组装清单字典（字段与 `PackageManifest.java` 一一对应）。"""
    if kind not in KIND_HAS_PLATFORM:
        raise ManifestError(f"不认识的形态 `{kind}`（可选：{'、'.join(KIND_HAS_PLATFORM)}）")
    needs_platform = KIND_HAS_PLATFORM[kind]
    if needs_platform and not platform:
        raise ManifestError(f"`{kind}` 的包带平台段，必须传 --platform")
    if not needs_platform and platform:
        raise ManifestError(f"`{kind}` 的包平台无关，不该传 --platform（给了 {platform}）")
    if not entries:
        raise ManifestError("包里没有任何文件，拒绝生成清单")
    if kind in NATIVE_KINDS and not graalvm:
        raise ManifestError(f"`{kind}` 的包由 GraalVM 构建，必须传 --graalvm（如 25.4）")
    if kind not in NATIVE_KINDS and graalvm:
        raise ManifestError(f"`{kind}` 是 JVM 包，不该传 --graalvm（给了 {graalvm}）")
    manifest = {
        "kind": kind,
        "version": version,
        "platform": platform if needs_platform else None,
        "jdk": jdk,
        "files": [entry.to_json() for entry in entries],
    }
    # `runtime` 段（jdk25 / graalvm25）是 JDK 级别；精确 GraalVM 版本单独记在 toolchain 里。
    if graalvm:
        manifest["toolchain"] = {"graalvm": graalvm}
    return manifest


def generate(zip_path: Path, manifest: dict) -> int:
    """把清单写进包根（幂等）。"""
    entries = read_entries(zip_path)
    if not entries:
        raise ManifestError(f"{zip_path}：包里没有任何文件")
    payload = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    # 用重写而不是追加：追加同名条目会留下两条，`unzip` 解出来的到底是哪条由实现决定。
    with tempfile.TemporaryDirectory() as tmp:
        rebuilt = Path(tmp) / zip_path.name
        with zipfile.ZipFile(zip_path) as source, zipfile.ZipFile(
                rebuilt, "w", zipfile.ZIP_DEFLATED) as target:
            for info in source.infolist():
                if info.filename == MANIFEST_NAME:
                    continue
                target.writestr(info, source.read(info.filename))
            target.writestr(MANIFEST_NAME, payload)
        shutil.move(str(rebuilt), str(zip_path))
    print(f"✅ 已写入清单：{zip_path} ← {len(entries)} 个文件"
          f"（{manifest['kind']} {manifest['version']}"
          f"{'' if not manifest['platform'] else ' ' + manifest['platform']} jdk{manifest['jdk']}）")
    return 0


def verify(zip_path: Path, kind: str | None, version: str | None,
           platform: str | None) -> int:
    """核对包内清单与包的真实内容：文件集、大小、sha256（可选：形态 / 版本 / 平台）。"""
    if not zip_path.is_file():
        raise ManifestError(f"文件不存在：{zip_path}")
    with zipfile.ZipFile(zip_path) as archive:
        try:
            raw = archive.read(MANIFEST_NAME).decode("utf-8")
        except KeyError:
            raise ManifestError(
                f"{zip_path}：包里没有 {MANIFEST_NAME}（该包不合格，不能用来自我替换）") from None
    declared = json.loads(raw)
    if kind is not None and declared.get("kind") != kind:
        raise ManifestError(f"{zip_path}：清单 kind 是 `{declared.get('kind')}`，期望 `{kind}`")
    if version is not None and declared.get("version") != version:
        raise ManifestError(
            f"{zip_path}：清单 version 是 `{declared.get('version')}`，期望 `{version}`")
    if platform is not None and declared.get("platform") != platform:
        raise ManifestError(
            f"{zip_path}：清单 platform 是 `{declared.get('platform')}`，期望 `{platform}`")

    actual = {entry.path: entry for entry in read_entries(zip_path)}
    declared_files = {}
    for item in declared.get("files") or []:
        path = item.get("path")
        check_entry_name(str(path), f"{zip_path} 的清单")
        declared_files[path] = item
    missing = sorted(set(actual) - set(declared_files))
    extra = sorted(set(declared_files) - set(actual))
    if missing:
        raise ManifestError(f"{zip_path}：清单漏了这些文件：{'、'.join(missing)}")
    if extra:
        raise ManifestError(f"{zip_path}：清单多出这些文件（包里没有）：{'、'.join(extra)}")
    for path, item in sorted(declared_files.items()):
        real = actual[path]
        if int(item.get("size", -1)) != real.size:
            raise ManifestError(
                f"{zip_path}：{path} 大小不符（清单 {item.get('size')}，实际 {real.size}）")
        if str(item.get("sha256", "")).lower() != real.sha256:
            raise ManifestError(
                f"{zip_path}：{path} 校验和不符（清单 {item.get('sha256')}，实际 {real.sha256}）")
    print(f"✅ 清单与包一致：{zip_path}（{len(declared_files)} 个文件，"
          f"{declared.get('kind')} {declared.get('version')}）")
    return 0


def sums(directory: Path, out: Path | None) -> int:
    """为目录下的每个 zip 写一行校验和（发布页资产 `SHA256SUMS`）。"""
    if not directory.is_dir():
        raise ManifestError(f"目录不存在：{directory}")
    zips = sorted(path for path in directory.glob("*.zip") if path.is_file())
    if not zips:
        raise ManifestError(f"{directory}：没有任何 zip，拒绝生成 {SUMS_NAME}")
    lines = [f"{sha256_file(path)}  {path.name}" for path in zips]
    target = out if out is not None else directory / SUMS_NAME
    target.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    print(f"✅ 已写入校验和：{target}（{len(lines)} 个资产）")
    for line in lines:
        print(f"   {line}")
    return 0


def self_test() -> int:
    """自检：正例（生成 → 校验 → 出校验和）与反例（改包后必须报错）。"""
    failures: list[str] = []
    with tempfile.TemporaryDirectory() as tmp:
        work = Path(tmp)
        package = work / "aha-desktop-0.1.3.00242-windows-amd64.zip"
        with zipfile.ZipFile(package, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("aha-desktop.jar", b"jar-bytes")
            archive.writestr("bin/aha.sh", b"#!/bin/sh\n")

        def expect_ok(label: str, action) -> None:
            try:
                action()
            except Exception as error:  # noqa: BLE001 - 自检就是要抓住任何异常
                failures.append(f"{label}：本该通过却失败：{error}")

        def expect_fail(label: str, action) -> None:
            try:
                action()
            except ManifestError:
                return
            except Exception as error:  # noqa: BLE001
                failures.append(f"{label}：抛了非 ManifestError：{type(error).__name__}: {error}")
                return
            failures.append(f"{label}：本该失败却通过了")

        expect_ok("生成清单", lambda: generate(package, build_manifest(
            "desktop-jvm", "0.1.3.00242", "windows-amd64", 25, read_entries(package))))
        native_entries = read_entries(package)
        native_manifest = build_manifest(
            "desktop-native", "0.1.3.00242", "windows-amd64", 25, native_entries, "25.4")
        if native_manifest.get("toolchain", {}).get("graalvm") != "25.4":
            failures.append("原生清单没有记录 toolchain.graalvm")
        expect_fail("原生包没给 --graalvm", lambda: build_manifest(
            "desktop-native", "0.1.3.00242", "windows-amd64", 25, native_entries))
        expect_fail("JVM 包给了 --graalvm", lambda: build_manifest(
            "desktop-jvm", "0.1.3.00242", "windows-amd64", 25, native_entries, "25.4"))
        expect_ok("校验清单", lambda: verify(package, "desktop-jvm", "0.1.3.00242", "windows-amd64"))
        expect_ok("再生成（幂等）", lambda: generate(package, build_manifest(
            "desktop-jvm", "0.1.3.00242", "windows-amd64", 25, read_entries(package))))
        expect_ok("出校验和", lambda: sums(work, work / SUMS_NAME))
        if not (work / SUMS_NAME).is_file():
            failures.append("出校验和：文件没有生成")

        # 反例：清单与包内容不符、形态/版本/平台不符、平台给错、垃圾条目、非法路径
        expect_fail("版本不符", lambda: verify(package, "desktop-jvm", "0.1.3.00241", None))
        expect_fail("形态不符", lambda: verify(package, "cli-jvm", None, None))
        expect_fail("平台不符", lambda: verify(package, None, None, "ubuntu-amd64"))
        expect_fail("CLI-JVM 没给平台（issue #269 起必须带平台段）", lambda: build_manifest(
            "cli-jvm", "0.1.3.00242", None, 25, read_entries(package)))
        expect_fail("带平台的形态没给平台", lambda: build_manifest(
            "desktop-jvm", "0.1.3.00242", None, 25, read_entries(package)))
        expect_fail("不认识的形态", lambda: build_manifest(
            "web", "0.1.3.00242", None, 25, read_entries(package)))
        expect_fail("空包", lambda: build_manifest(
            "cli-jvm", "0.1.3.00242", None, 25, read_entries(work / "nope.zip")))

        tampered = work / "aha-cli-0.1.3.00242.zip"
        shutil.copy(package, tampered)
        with zipfile.ZipFile(tampered, "a") as archive:
            archive.writestr("aha-cli.jar", b"extra")
        expect_fail("包里多了文件而清单没写", lambda: verify(tampered, None, None, None))

        junk = work / "junk.zip"
        with zipfile.ZipFile(junk, "w") as archive:
            archive.writestr("__MACOSX/._x", b"junk")
        expect_fail("打包垃圾", lambda: read_entries(junk))

        escaping = work / "escaping.zip"
        with zipfile.ZipFile(escaping, "w") as archive:
            archive.writestr("../evil.txt", b"evil")
        expect_fail("路径逃逸", lambda: read_entries(escaping))

    if failures:
        print("❌ 自检未通过：", file=sys.stderr)
        for failure in failures:
            print(f"   - {failure}", file=sys.stderr)
        return 1
    print("✅ 自检通过（正向 5 项 / 反向 11 项）")
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="生成 / 校验 AHA-PACKAGE.json 与 SHA256SUMS（issue #51 §6）")
    parser.add_argument("--self-test", action="store_true", help="跑内置自检后退出")
    sub = parser.add_subparsers(dest="command")

    gen = sub.add_parser("generate", help="把 AHA-PACKAGE.json 写进包根（幂等）")
    gen.add_argument("--zip", required=True)
    gen.add_argument("--kind", required=True, choices=sorted(KIND_HAS_PLATFORM))
    gen.add_argument("--version", required=True)
    gen.add_argument("--platform")
    gen.add_argument("--jdk", type=int, required=True)
    gen.add_argument("--graalvm", help="原生包的 GraalVM 精确版本（如 25.4），JVM 包不传")

    ver = sub.add_parser("verify", help="核对包内清单与包的真实内容")
    ver.add_argument("--zip", required=True)
    ver.add_argument("--kind")
    ver.add_argument("--version")
    ver.add_argument("--platform")

    sm = sub.add_parser("sums", help="为目录下的每个 zip 生成 SHA256SUMS")
    sm.add_argument("--dir", required=True)
    sm.add_argument("--out")

    args = parser.parse_args(argv)
    if args.self_test:
        return self_test()
    if args.command is None:
        parser.print_help()
        return 2
    try:
        if args.command == "generate":
            package = Path(args.zip)
            manifest = build_manifest(args.kind, args.version, args.platform, args.jdk,
                                      read_entries(package), args.graalvm)
            return generate(package, manifest)
        if args.command == "verify":
            return verify(Path(args.zip), args.kind, args.version, args.platform)
        return sums(Path(args.dir), Path(args.out) if args.out else None)
    except ManifestError as error:
        print(f"❌ {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
