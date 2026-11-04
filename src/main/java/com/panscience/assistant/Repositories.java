package com.panscience.assistant;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.*;
interface OrganizationRepository extends MongoRepository<Organization,String> {
 Optional<Organization> findByEmailIgnoreCase(String email);
 Optional<Organization> findByAssistantToken(String token);
}
interface ChunkRepository extends MongoRepository<KnowledgeChunk,String> {
 List<KnowledgeChunk> findByOrganizationId(String organizationId);
 void deleteByOrganizationIdAndDocumentId(String organizationId,String documentId);
}
interface KnowledgeDocumentRepository extends MongoRepository<KnowledgeDocument,String> {
 List<KnowledgeDocument> findByOrganizationIdOrderByUploadedAtDesc(String organizationId);
 Optional<KnowledgeDocument> findByIdAndOrganizationId(String id,String organizationId);
 long countByOrganizationId(String organizationId);
}
