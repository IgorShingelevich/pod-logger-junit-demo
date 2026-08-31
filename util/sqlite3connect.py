import sqlite3
from pathlib import Path
p = Path(__file__).with_name("pod-logger-store.sqlite")
c = sqlite3.connect(p)
print("tables", c.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall())
print("runs", c.execute("select id,test_run_name,status,started_at,finished_at from test_run").fetchall())
print("log_count", c.execute("select count(*) from log_entry").fetchall())
print("logs_by_method")
for row in c.execute(
    "select related_test_method, test_display_name, message, pod_name from log_entry order by timestamp"
):
    print(" ", row)
