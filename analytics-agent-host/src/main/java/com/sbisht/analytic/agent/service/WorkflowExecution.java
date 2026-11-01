package com.sbisht.analytic.agent.service;

import org.slf4j.Logger;

final class WorkflowExecution {
    private final long startedAt = System.nanoTime();
    private final String question;
    private WorkflowExecutionState state = WorkflowExecutionState.STARTED;
    private HandoffWorkflow workflow;

    private WorkflowExecution(String question) { this.question = question; }
    static WorkflowExecution start(String question) { return new WorkflowExecution(question); }

    void selectWorkflow(HandoffWorkflow workflow, Logger log) {
        this.workflow = workflow;
        log.info("Intent classified workflow={} questionLength={}", workflow, question.length());
    }

    void transitionTo(WorkflowExecutionState next, AgentControl activeAgent, Logger log) {
        log.info("Workflow state transition workflow={} from={} to={} activeAgent={}", workflow, state, next, activeAgent);
        state = next;
    }

    void waitingForHuman(Logger log) { transitionTo(WorkflowExecutionState.WAITING_FOR_HUMAN, AgentControl.HUMAN, log); }
    boolean isWaitingForHuman() { return state == WorkflowExecutionState.WAITING_FOR_HUMAN; }
    void complete(Logger log) { transitionTo(WorkflowExecutionState.COMPLETED, AgentControl.HOST, log); log.info("Workflow completed workflow={} durationMs={}", workflow, elapsedMillis()); }
    void fail(Logger log, RuntimeException error) { transitionTo(WorkflowExecutionState.FAILED, AgentControl.HOST, log); log.error("Workflow failed workflow={} durationMs={} error={}", workflow, elapsedMillis(), error.getMessage(), error); }
    long elapsedMillis() { return (System.nanoTime() - startedAt) / 1_000_000; }
}
