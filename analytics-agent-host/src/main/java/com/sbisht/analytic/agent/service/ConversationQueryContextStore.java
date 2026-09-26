package com.sbisht.analytic.agent.service;

import com.sbisht.analytic.agent.dto.QueryResult;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ConversationQueryContextStore {

    private final AtomicReference<ConversationQueryContext> defaultContext = new AtomicReference<>();

    public Optional<ConversationQueryContext> getLastContext() {
        return Optional.ofNullable(defaultContext.get());
    }

    public void save(String question, QueryResult queryResult) {
        defaultContext.set(new ConversationQueryContext(question, queryResult));
    }
}
