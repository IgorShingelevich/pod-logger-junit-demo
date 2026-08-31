# Команды: `pod-logger-junit-demo`

**Роль:** общий справочник команд проекта (commons). Не только тестирование: любые известные и используемые в разных ситуациях команды.

Каталог тестов (ожидания, приёмка, тонкости): [`PodLoggerJunitDemoTest.md`](PodLoggerJunitDemoTest.md).  
PRD: [`PodLoggerJunitDemoPRD.md`](PodLoggerJunitDemoPRD.md).
Команды рассчитаны на запуск из корня репозитория. Если `mvn` недоступен из `PATH`, сначала проверь окружение в скопе `Environment`.

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

Команды проверки окружения. JDK: **17+** (`pom` `release=17`).

```powershell
where.exe java
where.exe mvn.cmd
Get-Command mvn -ErrorAction SilentlyContinue
mvn -version
java -version
Write-Output "JAVA_HOME=$env:JAVA_HOME"
Write-Output "M2_HOME=$env:M2_HOME"
Write-Output "MAVEN_HOME=$env:MAVEN_HOME"
Write-Output "POD_LOGGER_STORE_PATH=$env:POD_LOGGER_STORE_PATH"
Write-Output "DOCKER_HOST=$env:DOCKER_HOST"
Write-Output "TESTCONTAINERS_RYUK_DISABLED=$env:TESTCONTAINERS_RYUK_DISABLED"
Get-ChildItem Env: | Where-Object { $_.Name -match 'JAVA|MAVEN|M2|DOCKER|POD_LOGGER|TESTCONTAINERS' }
```

---

## Docker / CTL

Docker Desktop и локальный CTL. `kubectl`/`oc` в демо-прогоне не вызывались. На Windows named pipe достаточен; `DOCKER_HOST=tcp://127.0.0.1:2375` не обязателен.

```powershell
docker version
docker version --format "{{.Server.Version}}"
docker info
docker info --format "ServerVersion={{.ServerVersion}} OperatingSystem={{.OperatingSystem}} Driver={{.Driver}}"
docker info --format "Containers={{.Containers}} Running={{.ContainersRunning}} Images={{.Images}}"
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Image}}"
docker build -t demo-api:local demo-app
```

---

## Build

Сборка артефактов без тестов. Jar `demo-app/target/demo-app.jar` нужен Dockerfile и `ClusterLifecycle`.

```powershell
mvn -DskipTests package
mvn -pl demo-app -am package -DskipTests
mvn -pl junit-pod-logger -am install -DskipTests
```

Зачем: поставить библиотеку в локальный `.m2`, чтобы `mvn -pl demo-tests test` не гонял library-тесты повторно (на Windows повтор часто ловит lock `@TempDir`).

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

Сверка as-built: [`Git`](#git) + файлы `docs/story/OpenShiftEventHandlingStory/`. Код: `PodLoggerExtension`, `PodLoggerService`, `OpenshiftClient`, `StandDownEventMatcher`.

```powershell
mvn -pl junit-pod-logger -am test -Dtest=OpenshiftEventHandlingTest
```

Подготовка среды и Docker — скопы [Environment](#environment), [Docker / CTL](#docker--ctl), [Build](#build).

### Сборка и тесты по модулям

```powershell
mvn -pl junit-pod-logger -am test
mvn -pl demo-app -am package -DskipTests
mvn -pl demo-tests -am test
mvn -pl demo-tests test
```

`mvn -pl demo-tests test` без `-am` использует уже установленный `junit-pod-logger` и не перезапускает library Surefire.

### DEBUG-прогон для миграции

Использовать, когда нужно быстро локализовать падение в `beforeAll`, `beforeEach`, `afterEach`, `afterAll`, cluster bootstrap или parser/client/store path.

```powershell
mvn -pl demo-tests -am test "-Dtest=OrderErrorIT" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dlogging.level.com.example.podlogger=DEBUG" "-Dlogging.level.com.example.demotest=DEBUG"
```

Зачем: включает step-level SLF4J debug для `PodLoggerExtension`, `PodLoggerService`, `ClusterLifecycle`, parser, client, store и Allure wiring.

### Чистый прогон (удалить артефакты прошлых запусков)

Перед приёмкой, разбором Allure/SQLite или когда нужна одна «свежая» картина — очистить каталоги отчётов и store demo-tests. Иначе `allure-results` и Surefire XML смешиваются с прошлыми прогонами.

```powershell
$cleanPaths = @(
  'demo-tests\target\allure-results',
  'demo-tests\target\surefire-reports',
  'demo-tests\target\site\allure-maven-plugin',
  'junit-pod-logger\target\allure-results',
  'junit-pod-logger\target\surefire-reports'
)
foreach ($p in $cleanPaths) { if (Test-Path $p) { Remove-Item $p -Recurse -Force } }
Remove-Item 'demo-tests\target\pod-logger-store.sqlite*' -Force -ErrorAction SilentlyContinue
```

Полный чистый прогон после очистки:

```powershell
mvn -pl demo-app -am package -DskipTests
mvn -pl demo-tests -am test
```

### Surefire / артефакты

```powershell
Get-ChildItem -Recurse -Filter 'TEST-*.xml' | Where-Object { $_.FullName -match '\\target\\surefire-reports\\' }
Get-ChildItem 'demo-tests\target' | Format-Table Name,Length,Mode
```

Разбор Surefire XML:

```powershell
$repoRoot = (Resolve-Path '.').Path
$reports = Get-ChildItem -Recurse -Filter 'TEST-*.xml' | Where-Object { $_.FullName -match '\\target\\surefire-reports\\' }
foreach ($file in $reports) {
  $relative = $file.FullName.Replace($repoRoot + '\', '')
  Write-Output "===== $relative ====="
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

## Allure

Команды Allure вне конкретного теста.

```powershell
mvn -pl demo-tests io.qameta.allure:allure-maven:2.15.0:report
```

List Allure results и generated report:

```powershell
Get-ChildItem 'demo-tests\target\allure-results' | Group-Object Extension
cmd /c "dir /b demo-tests\target\allure-results"
cmd /c "dir /b demo-tests\target\site\allure-maven-plugin"
Get-ChildItem -Recurse -Directory -Filter 'allure-results'
```

Разбор `*-result.json` (аттачи по каждому тесту):

```powershell
$resultsDir = 'demo-tests\target\allure-results'
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
Set-Location 'demo-tests\target\site\allure-maven-plugin'
python -m http.server 8765
curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:8765/
try { (Invoke-WebRequest -Uri 'http://127.0.0.1:8765/' -UseBasicParsing -TimeoutSec 5).StatusCode } catch { $_.Exception.Message }
```

Открыть в браузере:

- [http://127.0.0.1:8765/index.html](http://127.0.0.1:8765/index.html)
- Suites → `OrderErrorIT` → каждый parameterized кейс → Test body → `pod-logs-*` / `pod-events-*`

---

## Store

Команды осмотра SQLite store.

```powershell
Get-ChildItem -Recurse -Include '*.sqlite','*.sqlite-wal','*.sqlite-shm'
Get-Item 'demo-tests\target\pod-logger-store.sqlite' | Format-List FullName,Length,LastWriteTime
python -c "import sqlite3; p=r'demo-tests\target\pod-logger-store.sqlite'; c=sqlite3.connect(p); print('tables', c.execute(\"SELECT name FROM sqlite_master WHERE type='table'\").fetchall()); print('runs', c.execute('select id,test_run_name,status,started_at,finished_at from test_run').fetchall()); print('log_count', c.execute('select count(*) from log_entry').fetchall()); print('logs_by_method', c.execute('select related_test_method, test_display_name, message, pod_name from log_entry order by timestamp').fetchall())"
```

---

## Git

```powershell
git status --short
git branch -vv
git log --oneline -10
git diff --stat
```

Сверка канонических MD и event-story:

```powershell
git diff -- README.md docs/PodLoggerJunitDemoPRD.md docs/PodLoggerJunitDemoTest.md docs/PodLoggerJunitDemoCommands.md demo-app/demo-app.md demo-tests/demo-test.md junit-pod-logger/junit-pod-logger.md k8s/k8s.md docs/story/OpenShiftEventHandlingStory/OpenShiftEventHandlingStory.md
git diff -- docs/story/OpenShiftEventHandlingStory/
```

Сверка пакетных карт `junit-pod-logger` (дети модульного MD, не второй PRD):

```powershell
git diff -- junit-pod-logger/junit-pod-logger.md junit-pod-logger/src/main/java/com/example/podlogger/podLogger.md junit-pod-logger/src/main/java/com/example/podlogger/allure/allure.md junit-pod-logger/src/main/java/com/example/podlogger/client/openshiftClient.md junit-pod-logger/src/main/java/com/example/podlogger/event/event.md junit-pod-logger/src/main/java/com/example/podlogger/parser/logParser.md junit-pod-logger/src/main/java/com/example/podlogger/store/store.md junit-pod-logger/src/main/java/com/example/podlogger/store/repository/repository.md junit-pod-logger/src/main/java/com/example/podlogger/store/sqlite/sqlLite.md
```

Зачем: увидеть, изменилась ли карта модуля и пакетные MD вместе, без журнала сессии.

Сверка канона MD и копий YAML:

```powershell
git diff --stat
git ls-tree -r HEAD --name-only docs/
Get-ChildItem -Path . -Recurse -Filter '*.md' | Where-Object { $_.FullName -notmatch '\\target\\' } | ForEach-Object { $_.FullName.Replace((Get-Location).Path + '\','') }
fc.exe /b k8s\demo-api.yaml demo-tests\src\test\resources\k8s\demo-api.yaml
```

Зачем: список MD на диске и в каталоге `docs/`; бинарное сравнение двух копий манифеста.

---

## Cluster

Команды живого кластера (`kubectl`, `oc`, CRC/OKD). В текущем демо не использовались.

```powershell
# сюда: oc login, oc get pods, kubectl get events
```

---

## Риски миграции

| Риск | Симптом | Команда |
| --- | --- | --- |
| Агент запускает тесты без расширенного debug и теряет шаг падения | в логе есть только итоговая ошибка без понятного контекста | `DEBUG-прогон для миграции` выше |
| Выполняется не чистый прогон и артефакты смешиваются | Allure / Surefire / SQLite содержат следы старых запусков | `Чистый прогон` в разделе `Test Commands` |
| Проверяется не тот слой | гоняется полный demo path, хотя сначала нужен parser или store unit test | `По классам` в разделе `Test Commands` |

### Профит для агента в новом контуре

- Этот файл даёт готовые команды для пошаговой локализации проблемы без подбора аргументов Maven вручную.
- Для миграции особенно полезна связка: сначала `Чистый прогон`, затем `DEBUG-прогон для миграции`, затем точечные library-тесты.
