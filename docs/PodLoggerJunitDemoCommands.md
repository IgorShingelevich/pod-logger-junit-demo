# Команды: `pod-logger-junit-demo`

**Роль:** общий справочник команд проекта (commons). Не только тестирование: любые известные и используемые в разных ситуациях команды.

Каталог тестов (ожидания, приёмка, тонкости): [`PodLoggerJunitDemoTest.md`](PodLoggerJunitDemoTest.md).  
PRD: [`PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md).

Репозиторий: `C:\Users\V\pod-logger-junit-demo`.  
Рабочий Maven в сессии: `C:\Users\V\apache-maven-3.9.16`.  
Путь `C:\Program Files\apache-maven-3.9.16-bin.zip` — zip, не `bin`.  
`JAVA_HOME=C:\Program Files\Java\jdk-21`.

Как пополнять: новая команда попадает в **свой скоп**. Если команда нужна и для теста, и для эксплуатации — ссылка из карточки теста сюда, дублировать тело не обязательно.

Правило агента (каждый диалог в этом репо): [`.cursor/rules/demo-commands.mdc`](../.cursor/rules/demo-commands.mdc) — новые команды из терминала и ответов сразу дописывать сюда, без отдельной просьбы.

---

## Скопы

| Скоп | Раздел | Содержимое |
| --- | --- | --- |
| Environment | [Environment](#environment) | JDK, Maven, PATH, переменные среды |
| Docker / CTL | [Docker / CTL](#docker--ctl) | Docker Desktop, образы, контейнеры |
| Build | [Build](#build) | Сборка модулей без тестов |
| **Test Commands** | [Test Commands](#test-commands) | Прогон и проверка тестов |
| Allure | [Allure](#allure) | Генерация и открытие отчёта |
| Store | [Store](#store) | SQLite после прогона |
| Git | [Git](#git) | Статус и дифф документов |
| Cluster | [Cluster](#cluster) | K3s / OpenShift / `kubectl` / `oc` |

---

## Environment

Команды проверки и настройки окружения (не привязаны к одному тесту).

_Пока пусто как отдельный операционный скоп. Команды, которыми в сессии готовили JDK/Maven под тесты, лежат в [Test Commands → Проверка среды](#проверка-среды-jdk--maven--docker--env)._

```powershell
# сюда: java -version, mvn -version, JAVA_HOME, PATH, распаковка Maven
```

---

## Docker / CTL

Команды Docker Desktop и локального CTL. `kubectl`/`oc` в демо-прогоне не использовались: Fabric8 ходит в API K3s-контейнера сам.

_Пока пусто как отдельный операционный скоп. Команды, которыми проверяли Docker под тесты, лежат в [Test Commands → Проверка CTL](#проверка-ctl-docker--кластер)._

```powershell
# сюда: docker version, docker info, docker ps, docker build
```

---

## Build

Сборка артефактов без запуска тестов.

_Пока пусто как отдельный операционный скоп. Команды `mvn ... package -DskipTests`, использованные перед demo-tests, лежат в [Test Commands → Сборка и тесты по модулям](#сборка-и-тесты-по-модулям)._

```powershell
# сюда: mvn -pl demo-app -am package -DskipTests
```

---

## Test Commands

Команды прогона тестов, разбора Surefire, Event Handling analysis и проверки артефактов. Ожидания и критерии приёмки — только в [`PodLoggerJunitDemoTest.md`](PodLoggerJunitDemoTest.md).

Отчёт Allure и SQLite после прогона — в соседних скопах [Allure](#allure) и [Store](#store); они тоже использовались при проверке тестов.

CTL в тестовом прогоне — **Docker CLI** (K3s через Testcontainers).

### По классам

```powershell
mvn -pl junit-pod-logger -am test -Dtest=OpenshiftClientParseTest
mvn -pl junit-pod-logger -am test -Dtest=OpenshiftEventHandlingTest
mvn -pl junit-pod-logger -am test -Dtest=PersistentLogStoreTest
mvn -pl demo-tests -am test -Dtest=InfrastructureLoggingTest
mvn -pl demo-tests -am test -Dtest=OrderErrorIT
```

`InfrastructureLoggingTest` дополнительно (jar + Docker до Maven):

```powershell
mvn -pl demo-app -am package -DskipTests
docker info
docker build -t demo-api:local demo-app
mvn -pl demo-tests -am test -Dtest=InfrastructureLoggingTest
```

### Event handling analysis (не отдельная Maven-цель)

Сверка as-built vs PRD шла по файлам и git:

```powershell
git status --short
git diff -- README.md docs/README.md docs/prd/OpenShiftEventHandlingPRD.md docs/prd/EventHandlingPRD.md docs/EventHandling.PRD.md
```

После реорганизации смотреть:

- `docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md`
- `docs/story/OpenShiftEventHandlingStory/EventHandlingStrategies.md`
- `docs/story/OpenShiftEventHandlingStory/EventHandling2Story.md`
- код: `PodLoggerExtension`, `PodLoggerService`, `OpenshiftClient`, `StandDownEventMatcher`

Прогон приёмки Events без кластера:

```powershell
mvn -pl junit-pod-logger -am test -Dtest=OpenshiftEventHandlingTest
```

### Проверка среды (JDK / Maven / Docker / env)

```powershell
where.exe java
where.exe mvn.cmd
mvn -version
java -version
Write-Output "JAVA_HOME=$env:JAVA_HOME"
Write-Output "M2_HOME=$env:M2_HOME"
Write-Output "MAVEN_HOME=$env:MAVEN_HOME"
Write-Output "POD_LOGGER_STORE_PATH=$env:POD_LOGGER_STORE_PATH"
Write-Output "DOCKER_HOST=$env:DOCKER_HOST"
Write-Output "TESTCONTAINERS_RYUK_DISABLED=$env:TESTCONTAINERS_RYUK_DISABLED"
Get-ChildItem Env: | Where-Object { $_.Name -match 'JAVA|MAVEN|M2|DOCKER|POD_LOGGER|TESTCONTAINERS' }
Get-Command mvn
Get-ChildItem 'C:\Users\V\apache-maven-3.9.16\bin'
Get-Item 'C:\Program Files\apache-maven-3.9.16-bin.zip'
```

Поиск Maven (zip в Program Files — не runnable `bin`):

```powershell
Get-ChildItem -LiteralPath "C:\Program Files" -Directory | Where-Object { $_.Name -like "*maven*" -or $_.Name -like "*apache*" }
Get-ChildItem "C:\Program Files" -Filter "*maven*" -Recurse -Depth 3 -ErrorAction SilentlyContinue | Select-Object -First 30 FullName
$mvnHome = 'C:\Program Files\apache-maven-3.9.16-bin.zip\apache-maven-3.9.16'
Test-Path "$mvnHome\bin\mvn.cmd"
```

Распаковка Maven (один раз в диалоге):

```powershell
$dest = "C:\Users\V\apache-maven-3.9.16"
if (Test-Path $dest) { Write-Host "Already extracted at $dest" } else {
  Expand-Archive -LiteralPath "C:\Program Files\apache-maven-3.9.16-bin.zip" -DestinationPath "C:\Users\V" -Force
}
$env:PATH = "C:\Users\V\apache-maven-3.9.16\bin;$env:PATH"
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
```

### Проверка CTL (Docker / кластер)

```powershell
docker version
docker version --format "{{.Server.Version}}"
docker info
docker info --format "ServerVersion={{.ServerVersion}} OperatingSystem={{.OperatingSystem}} Driver={{.Driver}}"
docker info --format "Containers={{.Containers}} Running={{.ContainersRunning}} Images={{.Images}}"
docker info --format "Server: {{.ServerVersion}} OS: {{.OperatingSystem}} Running: {{.ContainersRunning}}"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Image}}"
docker build -t demo-api:local demo-app
```

### Сборка и тесты по модулям

```powershell
mvn -pl junit-pod-logger -am test
mvn -pl demo-app -am package -DskipTests
mvn -pl demo-tests -am test
```

С явным PATH сессии (как в диалоге, пока Maven не был в PATH терминала):

```powershell
$env:PATH = "C:\Users\V\apache-maven-3.9.16\bin;$env:PATH"; $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"; mvn -pl junit-pod-logger -am test
$env:PATH = "C:\Users\V\apache-maven-3.9.16\bin;$env:PATH"; $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"; mvn -pl demo-app -am package -DskipTests
$env:PATH = "C:\Users\V\apache-maven-3.9.16\bin;$env:PATH"; $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"; mvn -pl demo-tests -am test
```

### Surefire / артефакты

```powershell
Get-ChildItem -Recurse -Path 'C:\Users\V\pod-logger-junit-demo' -Filter 'TEST-*.xml' | Where-Object { $_.FullName -match '\\target\\surefire-reports\\' }
Get-ChildItem 'C:\Users\V\pod-logger-junit-demo\demo-tests\target' | Format-Table Name,Length,Mode
```

Разбор Surefire XML (как в диалоге):

```powershell
$reports = Get-ChildItem -Recurse -Path 'C:\Users\V\pod-logger-junit-demo' -Filter 'TEST-*.xml' | Where-Object { $_.FullName -match '\\target\\surefire-reports\\' }
foreach ($file in $reports) {
  Write-Output "===== $($file.FullName.Replace('C:\Users\V\pod-logger-junit-demo\','')) ====="
  [xml]$xml = Get-Content $file.FullName
  $suite = $xml.testsuite
  Write-Output ("SUITE tests={0} failures={1} errors={2} skipped={3} time={4}s" -f $suite.tests, $suite.failures, $suite.errors, $suite.skipped, $suite.time)
  foreach ($case in $suite.testcase) {
    $status = 'PASSED'
    $detail = ''
    if ($case.failure) { $status = 'FAILED'; $detail = (($case.failure.message) -replace '\s+', ' ') }
    if ($case.error) { $status = 'ERROR' }
    Write-Output ("  [{0}] {1}::{2}  time={3}s  {4}" -f $status, $case.classname, $case.name, $case.time, $detail)
  }
}
```

### Хронологический журнал команд диалога

Полный список Shell-вызовов сессии проверки тестов (40), в порядке выполнения:

1. `git status --short`
2. `git diff -- README.md docs/README.md docs/prd/OpenShiftEventHandlingPRD.md docs/prd/EventHandlingPRD.md docs/EventHandling.PRD.md`
3. повтор того же `git diff`
4. `mvn -pl junit-pod-logger -am test` (ещё до PATH Maven — не сработал)
5. `where.exe java`
6. `where.exe mvn.cmd`
7. поиск Maven в `C:\Program Files` (`Get-ChildItem` + zip)
8. `Expand-Archive` zip → `C:\Users\V\apache-maven-3.9.16`
9. PATH + `java -version` + `mvn -version` + `docker version` / `docker info`
10. `mvn -pl junit-pod-logger -am test` (с PATH)
11. `mvn -pl demo-app -am package -DskipTests`
12. `mvn -pl demo-tests -am test`
13. проверка persistence PATH (`Test-Path mvn.cmd`, user PATH, `where.exe mvn`)
14. `mvn -pl demo-tests allure:report` — prefix не найден
15. `mvn allure:report` из каталога `demo-tests` — prefix не найден
16. `mvn -pl demo-tests io.qameta.allure:allure-maven:2.15.0:report` — SUCCESS
17. запись Maven в PowerShell profile
18. повторная проверка Maven zip vs PATH + env + `docker version` / `docker info`
19. `mvn -version` + `Get-ChildItem` bin + `Get-Item` zip + `docker info` + `docker ps`
20. `mvn -pl junit-pod-logger -am test`
21. `mvn -pl demo-app -am package -DskipTests`
22. `mvn -pl demo-tests -am test`
23. разбор всех `TEST-*.xml` (Surefire)
24. list `demo-tests/target`, `allure-results`, sqlite
25. `Get-ChildItem demo-tests`, sqlite include, allure dirs
26. counts `allure-results` + `junit-pod-logger/allure-results`
27. `cmd /c dir /b ...\allure-results`
28. parse всех `*-result.json` (status, attachments)
29. latest result per test + `Get-Item` sqlite
30. dump аттачей четырёх `OrderErrorIT` кейсов
31. `python -c` sqlite3 inspect (`tables`, `test_run`, `log_entry`)
32. повтор `mvn -pl demo-tests allure:report`
33. `cmd /c dir` `.allure` / `allure.bat`
34. `cmd /c dir` `.allure\allure-2.30.0\bin`
35. `python ...\_inspect_store.py` (временный скрипт, удалён)
36. `mvn -pl demo-tests io.qameta.allure:allure-maven:2.15.0:report`
37. **list generated Allure Report:** `cmd /c dir /b ...\target\site\allure-maven-plugin`
38. `python -m http.server 8765` в каталоге report
39. `Invoke-WebRequest http://127.0.0.1:8765/`
40. `curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:8765/`

---

## Allure

Команды Allure вне конкретного теста. Тело, которым в сессии собирали и открывали отчёт demo-tests, пока лежит здесь же (скоп отчётности).

Prefix `allure:` из корня **не** резолвится:

```powershell
mvn -pl demo-tests allure:report
# No plugin found for prefix 'allure'
```

Рабочая генерация:

```powershell
mvn -pl demo-tests io.qameta.allure:allure-maven:2.15.0:report
```

List Allure results и generated report:

```powershell
Get-ChildItem 'C:\Users\V\pod-logger-junit-demo\demo-tests\target\allure-results' | Group-Object Extension
cmd /c "dir /b C:\Users\V\pod-logger-junit-demo\demo-tests\target\allure-results"
cmd /c "dir /b C:\Users\V\pod-logger-junit-demo\demo-tests\.allure"
cmd /c "dir /b C:\Users\V\pod-logger-junit-demo\demo-tests\.allure\allure-2.30.0"
cmd /c "dir /b C:\Users\V\pod-logger-junit-demo\demo-tests\.allure\allure-2.30.0\bin"
cmd /c "dir /s /b C:\Users\V\pod-logger-junit-demo\demo-tests\.allure\allure.bat"
cmd /c "dir /b C:\Users\V\pod-logger-junit-demo\demo-tests\target\site\allure-maven-plugin"
Get-ChildItem 'C:\Users\V\pod-logger-junit-demo' -Recurse -Directory -Filter 'allure-results'
```

Разбор `*-result.json` (аттачи по каждому тесту):

```powershell
$resultsDir = 'C:\Users\V\pod-logger-junit-demo\demo-tests\target\allure-results'
$files = Get-ChildItem $resultsDir -Filter '*-result.json'
Write-Output "result.json count=$($files.Count)"
foreach ($file in $files) {
  $j = Get-Content $file.FullName -Raw | ConvertFrom-Json
  $atts = @(); if ($j.attachments) { foreach ($a in $j.attachments) { $atts += ($a.name + '=>' + $a.source) } }
  Write-Output ("STATUS=$($j.status) | NAME=$($j.name) | ATTS=[$($atts -join '; ')]")
}
```

Открыть generated report по HTTP (из каталога report; `file://` JSON не грузит):

```powershell
Set-Location 'C:\Users\V\pod-logger-junit-demo\demo-tests\target\site\allure-maven-plugin'
python -m http.server 8765
curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:8765/
try { (Invoke-WebRequest -Uri 'http://127.0.0.1:8765/' -UseBasicParsing -TimeoutSec 5).StatusCode } catch { $_.Exception.Message }
```

Открыть в браузере:

- [http://127.0.0.1:8765/index.html](http://127.0.0.1:8765/index.html)
- Suites → `OrderErrorIT` → каждый parameterized кейс → Test body → `pod-logs-*` / `pod-events-*`

---

## Store

Команды осмотра SQLite store. Тело, которым смотрели `demo-tests/target/pod-logger-store.sqlite` после прогона, лежит здесь.

```powershell
Get-ChildItem -Recurse -Path 'C:\Users\V\pod-logger-junit-demo' -Include '*.sqlite','*.sqlite-wal','*.sqlite-shm'
Get-Item 'C:\Users\V\pod-logger-junit-demo\demo-tests\target\pod-logger-store.sqlite' | Format-List FullName,Length,LastWriteTime
python -c "import sqlite3; p=r'C:\Users\V\pod-logger-junit-demo\demo-tests\target\pod-logger-store.sqlite'; c=sqlite3.connect(p); print('tables', c.execute(\"SELECT name FROM sqlite_master WHERE type='table'\").fetchall()); print('runs', c.execute('select id,test_run_name,status,started_at,finished_at from test_run').fetchall()); print('log_count', c.execute('select count(*) from log_entry').fetchall()); print('logs_by_method', c.execute('select related_test_method, test_display_name, message, pod_name from log_entry order by timestamp').fetchall())"
```

---

## Git

Команды git вне тестового прогона.

_Пока пусто как отдельный операционный скоп. `git status` / `git diff` сессии сверки Event Handling лежат в [Test Commands → Event handling analysis](#event-handling-analysis-не-отдельная-maven-цель)._

```powershell
# сюда: git status, git diff, git log
```

---

## Cluster

Команды живого кластера (`kubectl`, `oc`, CRC/OKD). В текущем демо не использовались.

```powershell
# сюда: oc login, oc get pods, kubectl get events
```
