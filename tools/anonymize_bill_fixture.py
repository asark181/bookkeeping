"""账单测试数据脱敏：同一映射作用于 CSV 测试文本、xlsx 与断言，保证测试含义不变。

用法：
    python3 tools/anonymize_bill_fixture.py text <源.kt> <目标.kt>
    python3 tools/anonymize_bill_fixture.py xlsx <源.xlsx> <目标.xlsx>

内置通用规则：银行卡尾号「(1234)」统一替换、16 位以上的交易单号/商户单号替换为同长度的伪随机数字
（同一号码在 CSV、xlsx、断言中映射一致）、xlsx 作者信息清空。
真实人名、门店、地点等映射写在 tools/fixture_names.local.json（已被 .gitignore 忽略，绝不提交），格式：
    [["真实名称", "替换名称"], ...]
脱敏后用 grep 确认无残留，再提交。
"""
import hashlib
import json
import os
import re
import sys
import zipfile

LOCAL_NAMES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixture_names.local.json")


def load_names():
    if not os.path.exists(LOCAL_NAMES):
        return []
    with open(LOCAL_NAMES, encoding="utf-8") as f:
        return [tuple(pair) for pair in json.load(f)]


NAMES = load_names()


def fake_digits(m):
    run = m.group(0)
    h = hashlib.sha256(("bookkeeping-fixture:" + run).encode()).hexdigest()
    digits = "".join(str(int(c, 16) % 10) for c in (h * 4))
    return digits[: len(run)]


def scrub(text: str) -> str:
    # 长名称优先替换，避免短名称先命中长名称的一部分
    for a, b in sorted(NAMES, key=lambda p: -len(p[0])):
        text = text.replace(a, b)
    text = re.sub(r"(银行[^,()（）]{0,8})\((\d{4})\)", r"\1(0000)", text)
    return re.sub(r"(?<![0-9])[0-9]{16,}(?![0-9])", fake_digits, text)


def main():
    mode, src, dst = sys.argv[1:4]
    if mode == "text":
        with open(src, encoding="utf-8") as f:
            data = f.read()
        with open(dst, "w", encoding="utf-8") as f:
            f.write(scrub(data))
        return
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            if info.filename.endswith((".xml", ".rels")):
                s = scrub(data.decode("utf-8"))
                s = re.sub(r"<dc:creator>[^<]*</dc:creator>", "<dc:creator></dc:creator>", s)
                s = re.sub(r"<cp:lastModifiedBy>[^<]*</cp:lastModifiedBy>", "<cp:lastModifiedBy></cp:lastModifiedBy>", s)
                data = s.encode("utf-8")
            zout.writestr(info, data)


if __name__ == "__main__":
    main()
