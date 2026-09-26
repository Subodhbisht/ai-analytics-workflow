package com.sbisht.analytic.agent.service;

import com.sbisht.analytic.agent.dto.AnalyticsRequest;
import com.sbisht.analytic.agent.dto.AnalyticsResponse;
import com.sbisht.analytic.agent.dto.TextInsight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

	private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
	private final ChatClient summarizerChatClient;
	private final SqlExecutionPipeline sqlExecutionPipeline;
	private final PromptIntentRouter promptIntentRouter;
	private final ConversationQueryContextStore contextStore;
	private final ReasonCodeKnowledgeBase reasonCodeKnowledgeBase;

	public AnalyticsService(@Qualifier("Summarizer") ChatClient summarizerChatClient,
							SqlExecutionPipeline sqlExecutionPipeline,
							PromptIntentRouter promptIntentRouter,
							ConversationQueryContextStore contextStore,
							ReasonCodeKnowledgeBase reasonCodeKnowledgeBase) {
		this.summarizerChatClient = summarizerChatClient;
		this.sqlExecutionPipeline = sqlExecutionPipeline;
		this.promptIntentRouter = promptIntentRouter;
		this.contextStore = contextStore;
		this.reasonCodeKnowledgeBase = reasonCodeKnowledgeBase;
	}

	public AnalyticsResponse analyze(AnalyticsRequest request) {
		log.info("Got the user request : {}", request);
		if (request == null || request.message() == null || request.message().isBlank()) {
			return noChart("Please ask a question so I can analyze it.");
		}

		var message = request.message().strip();
		var lastContext = contextStore.getLastContext();
		var intentDecision = promptIntentRouter.route(message, lastContext);
		log.info("Intent decision: {}", intentDecision);

		return switch (intentDecision.intent()) {
			case NEW_SQL -> analyzeNewSql(message, false);
			case SQL_PLUS_RAG -> analyzeNewSql(message, true);
			case FOLLOWUP_ON_LAST_RESULT -> lastContext
					.map(context -> analyzePreviousResult(message, context))
					.orElseGet(() -> noChart("I need a previous SQL result before I can answer that follow-up."));
			case DOC_RAG -> answerFromReasonCodeDocs(message);
			case CLARIFY -> noChart("I need a little more detail before I can choose whether to query data or use the previous result.");
		};
	}

	private AnalyticsResponse analyzeNewSql(String message, boolean includeReasonCodeContext) {
		var queryResult = this.sqlExecutionPipeline.run(message);
		contextStore.save(message, queryResult);
		log.info("Found {} matching rows. Applying insight ", queryResult.rows().size());
		var retrievedDocumentation = includeReasonCodeContext
				? documentationSection(reasonCodeKnowledgeBase.retrieveWithFallback(message, queryResult, 8))
				: "";

		return summarizerChatClient.prompt().user("""
              User Question: %s

              Executed SQL:

              %s

              SQL Result:

              %s
              %s
              """.formatted(
						message,
						queryResult.sql(),
						queryResult.rows(),
						retrievedDocumentation))
				.call()
				.entity(AnalyticsResponse.class);
	}

	private AnalyticsResponse analyzePreviousResult(String message, ConversationQueryContext context) {
		var documentationContext = documentationSection(reasonCodeKnowledgeBase.retrieve(message, context.queryResult(), 5));

		return summarizerChatClient.prompt().user("""
              User Follow-up:
              %s

              Previous User Question:
              %s

              Previous Executed SQL:

              %s

              Previous SQL Result:

              %s
              %s
              """.formatted(
						message,
						context.question(),
						context.sql(),
						context.queryResult().rows(),
						documentationContext))
				.call()
				.entity(AnalyticsResponse.class);
	}

	private AnalyticsResponse answerFromReasonCodeDocs(String message) {
		var documentationContext = reasonCodeKnowledgeBase.format(reasonCodeKnowledgeBase.retrieve(message, 5));
		var insight = summarizerChatClient.prompt().user("""
              User Question:
              %s

              Retrieved Onboarding Reason-Code Documentation:

              %s

              Answer using only the retrieved documentation. Return concise Markdown in the insight field.
              """.formatted(message, documentationContext))
				.call()
				.entity(TextInsight.class);

		return noChart(insight.insight());
	}

	private String documentationSection(java.util.List<ReasonCodeKnowledgeBase.ReasonCodeChunk> chunks) {
		if (chunks.isEmpty()) {
			return "";
		}

		return """

              Retrieved Onboarding Reason-Code Documentation:

              %s
              """.formatted(reasonCodeKnowledgeBase.format(chunks));
	}

	private AnalyticsResponse noChart(String insight) {
		return new AnalyticsResponse(insight, null, null, null);
	}
}
