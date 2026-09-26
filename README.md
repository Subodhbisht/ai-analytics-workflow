# AI Analytics Workflow

This application answers analytics questions by handing work to three specialized MCP agents.
The Java host owns the UI, intent routing, workflow state, authorization policy, and final response.

## Modules

| Module | Port | Responsibility |
| --- | ---: | --- |
| `analytics-agent-host` | 8083 | UI, intent routing, handoffs, workflow state, and fallback responses |
| `sql-agent-mcp-server` | 8084 | SQL generation, validation, repair, and read-only execution |
| `knowledge-rag-agent-mcp-server` | 8085 | Document retrieval and grounded answers |
| `result-analysis-agent-mcp-server` | 8086 | SQL result interpretation, summaries, insights, and chart data |
| `analytics-agent-contracts` | - | Shared request and response objects |

## Request Flow

```text
User
  |
  v
Analytics Host
  |
  +-- NEW_SQL -------> SQL Agent -------> Result Analysis Agent
  |
  +-- DOC_RAG -------> Knowledge/RAG Agent
  |
  +-- SQL_PLUS_RAG --> SQL Agent -------> Knowledge/RAG Agent
  |                                      |
  |                                      v
  |                               Result Analysis Agent
  |
  +-- FOLLOW_UP -----> Previous SQL result -> RAG context -> Result Analysis Agent
  |
  +-- CLARIFY -------> Ask the user for more information
```

Routing is deterministic. The host classifies the request and directly selects the agent that receives control.

## SQL Flow

The host makes one MCP call to the SQL agent.

```text
Question
  -> Generate SQL
  -> Validate read-only policy
  -> Repair when validation or execution fails
  -> Execute SQL
  -> Return structured rows
```

The host authorizes read-only access and provides the retry limit. The SQL agent performs the complete operation inside that single MCP request.

## Requirements

- Java 25
- Ollama running at `http://127.0.0.1:11434`
- Ollama model `qwen3:14b`
- PostgreSQL database configured for the SQL agent
- Gemini API key for the RAG and result-analysis agents

Set the Gemini key in `secrets.env` at the project root:

```properties
GEMINI_API_KEY=your-api-key
```

Update the database connection in:

```text
sql-agent-mcp-server/src/main/resources/application.properties
```

## Build And Test

Run from the project root:

```bash
./analytics-agent-host/mvnw -f pom.xml clean test
./analytics-agent-host/mvnw -f pom.xml package -DskipTests
```

## Start The Application

Start each application in a separate terminal. Start the three agents first, then the host.

```bash
java -jar sql-agent-mcp-server/target/sql-agent-mcp-server-0.0.1-SNAPSHOT.jar
```

```bash
java -jar knowledge-rag-agent-mcp-server/target/knowledge-rag-agent-mcp-server-0.0.1-SNAPSHOT.jar
```

```bash
java -jar result-analysis-agent-mcp-server/target/result-analysis-agent-mcp-server-0.0.1-SNAPSHOT.jar
```

```bash
java -jar analytics-agent-host/target/analytics-agent-host-0.0.1-SNAPSHOT.jar
```

Open the UI at [http://localhost:8083](http://localhost:8083).

## API Example

```bash
curl -X POST http://localhost:8083/api/analytics \
  -H "Content-Type: application/json" \
  -d '{"message":"Count onboarding events by reason code"}'
```

## Timeout And Fallback

The MCP request timeout is 120 seconds by default. It can be changed when starting the host:

```bash
ANALYTICS_MCP_REQUEST_TIMEOUT=180s \
  java -jar analytics-agent-host/target/analytics-agent-host-0.0.1-SNAPSHOT.jar
```

MCP clients connect lazily, so the host can start even when an agent is unavailable. If a selected agent times out or cannot be reached, the UI receives a normal response such as:

```text
The SQL agent needed to resolve this query is not responding. Please try again shortly.
```

Logs show intent selection, workflow state changes, MCP handoffs, retries, row counts, failures, and execution duration.
