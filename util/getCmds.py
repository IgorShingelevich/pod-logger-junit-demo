import json
import re

path = r"C:\Users\V\.cursor\projects\c-Users-V-pod-logger-junit-demo\agent-transcripts\0f6f272a-7680-4900-abc1-70beec57beb4\0f6f272a-7680-4900-abc1-70beec57beb4.jsonl"
cmds = []
with open(path, encoding="utf-8") as f:
    for i, line in enumerate(f, 1):
        try:
            o = json.loads(line)
        except Exception:
            continue
        msg = o.get("message", {})
        content = msg.get("content") if isinstance(msg, dict) else None
        if isinstance(content, list):
            for part in content:
                if not isinstance(part, dict):
                    continue
                name = part.get("name")
                if part.get("type") == "tool_use" and name == "Shell":
                    inp = part.get("input") or {}
                    c = inp.get("command")
                    if c:
                        cmds.append((i, c))
        elif isinstance(content, str):
            for m in re.finditer(r'"command"\s*:\s*"((?:\\.|[^"\\])*)"', content):
                raw = m.group(1).encode("utf-8").decode("unicode_escape")
                cmds.append((i, raw))

print("COUNT", len(cmds))
out = r"c:\Users\V\pod-logger-junit-demo\_extracted_cmds.txt"
with open(out, "w", encoding="utf-8") as w:
    for n, (ln, c) in enumerate(cmds, 1):
        w.write(f"===== {n} transcript_line={ln} =====\n")
        w.write(c)
        w.write("\n\n")
print("wrote", out)
