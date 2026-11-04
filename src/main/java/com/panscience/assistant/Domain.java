package com.panscience.assistant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.*;
class ApiModels {
 record RegisterRequest(String organizationName, String email, String password) {}
 record LoginRequest(String email, String password) {}
 record AuthResponse(String token, String organizationName, String email) {}
 record AskRequest(String question) {}
 record SourceRef(String document, int page, String excerpt) {}
 record AskResponse(String answer, List<SourceRef> sources, Map<String,Object> decisions, boolean answered) {}
}
@Document("organizations")
class Organization {
 @Id public String id;
 public String name, email, passwordHash, assistantToken;
 public Instant createdAt = Instant.now();
 public long questionsAsked, questionsAnswered, unanswered;
}
@Document("knowledge_chunks")
class KnowledgeChunk {
 @Id public String id;
 public String organizationId, documentId, fileName, text;
 public int page;
}
@Document("documents")
class KnowledgeDocument {
 @Id public String id;
 public String organizationId, fileName, contentType;
 public long size;
 public int pages, chunkCount;
 public Instant uploadedAt = Instant.now();
}
