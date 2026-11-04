package com.panscience.assistant;
import static com.panscience.assistant.ApiModels.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.*;
import org.springframework.stereotype.Service;

@RestController
@RequestMapping("/api")
class ApiController {
 private final OrganizationRepository orgs; private final KnowledgeDocumentRepository docs; private final ChunkRepository chunks;
 private final JwtService jwt; private final KnowledgeEngine engine; private final String baseUrl;
 private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
 ApiController(OrganizationRepository o, KnowledgeDocumentRepository d, ChunkRepository c, JwtService j, KnowledgeEngine e,@Value("${app.base-url}") String b){orgs=o;docs=d;chunks=c;jwt=j;engine=e;baseUrl=b;}
 @PostMapping("/auth/register") AuthResponse register(@RequestBody RegisterRequest r){
  if(r.organizationName()==null||r.organizationName().isBlank()||r.email()==null||!r.email().contains("@")||r.password()==null||r.password().length()<8) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Organization, valid email and password (8+ characters) are required");
  if(orgs.findByEmailIgnoreCase(r.email()).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered");
  Organization o=new Organization(); o.name=r.organizationName().trim();o.email=r.email().trim().toLowerCase();o.passwordHash=encoder.encode(r.password());o.assistantToken=randomToken();orgs.save(o);
  return new AuthResponse(jwt.create(o.id),o.name,o.email);
 }
 @PostMapping("/auth/login") AuthResponse login(@RequestBody LoginRequest r){
  Organization o=orgs.findByEmailIgnoreCase(r.email()==null?"":r.email()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password"));
  if(r.password()==null||!encoder.matches(r.password(),o.passwordHash)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
  return new AuthResponse(jwt.create(o.id),o.name,o.email);
 }
 private String owner(Authentication a){return (String)a.getPrincipal();}
 @GetMapping("/dashboard") Map<String,Object> dashboard(Authentication a){
  String id=owner(a); Organization o=orgs.findById(id).orElseThrow();
  return Map.ofEntries(
   Map.entry("organizationName",o.name),
   Map.entry("email",o.email),
   Map.entry("assistantToken",o.assistantToken),
   Map.entry("assistantUrl",baseUrl+"/assistant/"+o.assistantToken),
   Map.entry("documents",docs.findByOrganizationIdOrderByUploadedAtDesc(id)),
   Map.entry("questionsAsked",o.questionsAsked),
   Map.entry("questionsAnswered",o.questionsAnswered),
   Map.entry("unanswered",o.unanswered),
   Map.entry("documentLimit",10),
   Map.entry("maxPages",20),
   Map.entry("maxBytes",10485760)
  );
 }
 @GetMapping("/documents") List<KnowledgeDocument> documents(Authentication a){return docs.findByOrganizationIdOrderByUploadedAtDesc(owner(a));}
 @PostMapping(value="/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) KnowledgeDocument upload(@RequestPart("file") MultipartFile f,Authentication a) throws IOException {
  String id=owner(a);
  if(f.isEmpty()||f.getSize()>10485760) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PDF must be non-empty and no larger than 10 MB");
  if(docs.countByOrganizationId(id)>=10) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Maximum 10 PDFs per organization reached");
  if(!"application/pdf".equalsIgnoreCase(f.getContentType()) && (f.getOriginalFilename()==null || !f.getOriginalFilename().toLowerCase().endsWith(".pdf"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only PDF files are allowed");
  try(PDDocument pdf=Loader.loadPDF(f.getBytes())){
   int pages=pdf.getNumberOfPages(); if(pages<1||pages>20) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PDF must contain between 1 and 20 pages");
   KnowledgeDocument d=new KnowledgeDocument(); d.organizationId=id;d.fileName=Optional.ofNullable(f.getOriginalFilename()).orElse("document.pdf");d.contentType="application/pdf";d.size=f.getSize();d.pages=pages;
   PDFTextStripper stripper=new PDFTextStripper(); List<KnowledgeChunk> pending=new ArrayList<>();
   for(int p=1;p<=pages;p++){stripper.setStartPage(p);stripper.setEndPage(p);String text=stripper.getText(pdf).trim(); if(!text.isBlank()) for(String part:engine.chunk(text)){
    KnowledgeChunk ch=new KnowledgeChunk();ch.organizationId=id;ch.fileName=d.fileName;ch.page=p;ch.text=part;pending.add(ch);
   }}
   if(pending.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This PDF contains no extractable text. Scanned PDFs require OCR and are not supported.");
   docs.save(d); pending.forEach(ch->ch.documentId=d.id); chunks.saveAll(pending); d.chunkCount=pending.size(); docs.save(d); return d;
  } catch(ResponseStatusException ex){throw ex;} catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unable to process PDF. Ensure it is a valid, text-readable PDF.");}
 }
 @DeleteMapping("/documents/{docId}") ResponseEntity<Void> delete(@PathVariable String docId,Authentication a){
  String id=owner(a); KnowledgeDocument d=docs.findByIdAndOrganizationId(docId,id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Document not found"));
  chunks.deleteByOrganizationIdAndDocumentId(id,docId);docs.delete(d);return ResponseEntity.noContent().build();
 }
 @PostMapping("/assistant/regenerate") Map<String,String> regenerate(Authentication a){
  Organization o=orgs.findById(owner(a)).orElseThrow();o.assistantToken=randomToken();orgs.save(o);return Map.of("assistantToken",o.assistantToken,"assistantUrl",baseUrl+"/assistant/"+o.assistantToken);
 }
 @GetMapping("/assistant/{token}/config") Map<String,String> config(@PathVariable String token){
  Organization o=orgs.findByAssistantToken(token).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invalid assistant link"));
  return Map.of("organizationName",o.name);
 }
 @PostMapping("/public/{token}/ask") AskResponse ask(@PathVariable String token,@RequestBody AskRequest r){
  if(r.question()==null||r.question().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Please enter a question");
  Organization o=orgs.findByAssistantToken(token).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Invalid assistant token"));
  AskResponse response=engine.answer(o,r.question().trim(),chunks.findByOrganizationId(o.id));
  o.questionsAsked++;if(response.answered())o.questionsAnswered++;else o.unanswered++;orgs.save(o);return response;
 }
 private String randomToken(){byte[] b=new byte[24];new SecureRandom().nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
}

@Service
class KnowledgeEngine {
 private final LlmAnswerService llm;
 private final java.util.concurrent.Executor decisionExecutor = java.util.concurrent.ForkJoinPool.commonPool();
 KnowledgeEngine(LlmAnswerService llm){this.llm=llm;}
 List<String> chunk(String text){
  List<String> out=new ArrayList<>(); String normalized=text.replaceAll("\\s+"," ").trim();
  // Keep chunks page-local (page metadata is assigned by the caller).
  for(int i=0;i<normalized.length();i+=900) out.add(normalized.substring(i,Math.min(normalized.length(),i+900)));
  return out;
 }
 AskResponse answer(Organization org,String q,List<KnowledgeChunk> all){
  List<String> terms=terms(q);
  // Tenant scope is enforced by the repository call and repeated defensively here.
  List<KnowledgeChunk> tenantChunks=all.stream().filter(c->org.id.equals(c.organizationId)).toList();
  List<Scored> ranked=tenantChunks.stream().map(c->new Scored(c,bm25Like(c.text,terms,tenantChunks))).filter(x->x.score>0)
    .sorted((a,b)->Double.compare(b.score,a.score)).limit(5).toList();
  if(ranked.isEmpty() || ranked.get(0).score<0.035){
   return new AskResponse("I couldn't find this information in "+org.name+"'s knowledge base. Please contact the organization for confirmation.",List.of(),
    Map.of("classification","KNOWLEDGE_GAP","retrievalScore",ranked.isEmpty()?0:round(ranked.get(0).score),"decomposition",List.of(q),"decisionEngine","System-1 decision pipeline"),false);
  }
  List<SourceRef> refs=ranked.stream().map(x->new SourceRef(x.chunk.fileName,x.chunk.page,x.chunk.text.substring(0,Math.min(240,x.chunk.text.length())))).toList();
  String context=ranked.stream().map(x->x.chunk.text).collect(Collectors.joining("\n"));
  List<String> understood=llm.understand(q); final List<String> subQuestions=understood.isEmpty()?decompose(q):understood;
  // Independent System-1 models run concurrently and are combined by application logic.
  var choice=java.util.concurrent.CompletableFuture.supplyAsync(()->choiceModel(q,context),decisionExecutor);
  var bool=java.util.concurrent.CompletableFuture.supplyAsync(()->booleanModel(q,context,subQuestions),decisionExecutor);
  var score=java.util.concurrent.CompletableFuture.supplyAsync(()->scoreModel(q,ranked,subQuestions),decisionExecutor);
  Map<String,Object> decisions=new LinkedHashMap<>();
  try {
   decisions.put("classification",choice.join()); decisions.put("conditionChecks",bool.join()); decisions.put("evidenceScore",score.join());
  } catch(Exception ex){ decisions.put("decisionStatus","PARTIAL_FAILURE"); decisions.put("decisionError","A decision model could not complete"); }
  decisions.put("decomposition",subQuestions); decisions.put("combinedOutcome",combine(decisions));
  decisions.put("decisionEngine","Parallel deterministic System-1 models: Choice + Boolean + Score");
  String answer=llm.generate(q,context,decisions,refs);
  return new AskResponse(answer,refs,decisions,true);
 }
 private List<String> terms(String q){
  Set<String> stop=Set.of("the","and","for","with","from","that","this","what","when","where","which","does","have","has","how","can","could","would","should","are","was","were","you","your","about","tell","please","into","than","then","who","why");
  return Arrays.stream(q.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]"," ").split("\\s+"))
   .filter(t->t.length()>2&&!stop.contains(t)).distinct().toList();
 }
 private double bm25Like(String text,List<String> terms,List<KnowledgeChunk> corpus){
  if(terms.isEmpty())return 0; String lower=text.toLowerCase(Locale.ROOT); double score=0;
  for(String term:terms){int tf=count(lower,term); if(tf>0){long df=corpus.stream().filter(c->c.text.toLowerCase(Locale.ROOT).contains(term)).count(); double idf=Math.log(1.0+(corpus.size()-df+0.5)/(df+0.5)); score+=idf*(tf*2.2)/(tf+1.2);}}
  return score/Math.max(1,terms.size());
 }
 private int count(String text,String term){int n=0,at=0;while((at=text.indexOf(term,at))>=0){n++;at+=term.length();}return n;}
 private List<String> decompose(String q){
  String cleaned=q.trim().replaceAll("(?i)\\b(and also|also|additionally)\\b", " and ");
  String[] parts=cleaned.split("(?i)\\s+(?:and|while|whereas|plus)\\s+|[,;?]+\\s*");
  List<String> result=Arrays.stream(parts).map(String::trim).filter(x->x.length()>3).distinct().limit(6).toList();
  return result.isEmpty()?List.of(q):result;
 }
 private Map<String,Object> choiceModel(String q,String context){
  String s=q.toLowerCase(Locale.ROOT), c=context.toLowerCase(Locale.ROOT); String category="GENERAL";
  if(s.contains("scholarship")||c.contains("scholarship"))category="SCHOLARSHIP";
  else if(s.contains("refund")||c.contains("refund"))category="REFUND";
  else if(s.contains("admission")||c.contains("admission"))category="ADMISSION";
  else if(s.contains("attendance")||c.contains("attendance"))category="ATTENDANCE";
  return Map.of("type","CHOICE","value",category);
 }
 private List<Map<String,Object>> booleanModel(String q,String context,List<String> conditions){
  String c=context.toLowerCase(Locale.ROOT); List<Map<String,Object>> checks=new ArrayList<>();
  for(String condition:conditions){List<String> ts=terms(condition); long hits=ts.stream().filter(c::contains).count();
   checks.add(Map.of("condition",condition,"type","BOOLEAN","evidenceFound",hits>0,"matchedTerms",hits,"totalTerms",ts.size()));
  }
  return checks;
 }
 private Map<String,Object> scoreModel(String q,List<Scored> ranked,List<String> conditions){
  double top=ranked.isEmpty()?0:ranked.get(0).score; double coverage=conditions.isEmpty()?0:(double)conditions.stream().filter(x->ranked.stream().anyMatch(r->terms(x).stream().anyMatch(t->r.chunk.text.toLowerCase(Locale.ROOT).contains(t)))).count()/conditions.size();
  return Map.of("type","SCORE","retrievalRelevance",round(Math.min(1,top)),"conditionCoverage",round(coverage),"evidenceStrength",round(Math.min(1,(Math.min(1,top)*0.6)+(coverage*0.4))));
 }
 private String combine(Map<String,Object> decisions){
  Object raw=decisions.get("conditionChecks"); if(raw instanceof List<?> list&&!list.isEmpty()){
   long found=list.stream().filter(x->x instanceof Map<?,?> m&&Boolean.TRUE.equals(m.get("evidenceFound"))).count();
   if(found==0)return "INSUFFICIENT_POLICY_EVIDENCE"; if(found<list.size())return "PARTIAL_POLICY_EVIDENCE";
  }
  Object s=decisions.get("evidenceScore"); if(s instanceof Map<?,?> m&&m.get("evidenceStrength") instanceof Number n&&n.doubleValue()<0.25)return "LOW_CONFIDENCE";
  return "EVIDENCE_RETRIEVED_REQUIRES_POLICY_INTERPRETATION";
 }
 private double round(double n){return Math.round(n*1000.0)/1000.0;}
 private record Scored(KnowledgeChunk chunk,double score){}
}
@Service
class LlmAnswerService {
 @Value("${app.llm-base-url}") String base; @Value("${app.llm-api-key}") String key; @Value("${app.llm-model}") String model;
 List<String> understand(String question){
  if(base==null||base.isBlank()||key==null||key.isBlank()) return List.of();
  try {
   var body=Map.of("model",model,"temperature",0,"messages",List.of(
    Map.of("role","system","content","Break the user question into up to 6 short, independently checkable sub-questions or conditions. Return ONLY a JSON array of strings. Do not answer the question."),
    Map.of("role","user","content",question)));
   var client=java.net.http.HttpClient.newHttpClient();
   var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base.replaceAll("/+$","")+"/chat/completions")).header("Authorization","Bearer "+key).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body))).build();
   var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()<200||response.statusCode()>=300)return List.of();
   var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());
   String content=node.path("choices").path(0).path("message").path("content").asText("").replaceAll("(?s)^```(?:json)?\\s*|\\s*```$","");
   var arr=new com.fasterxml.jackson.databind.ObjectMapper().readTree(content);
   if(!arr.isArray())return List.of(); List<String> out=new ArrayList<>(); arr.forEach(x->{if(x.isTextual()&&!x.asText().isBlank()&&out.size()<6)out.add(x.asText().trim());}); return out;
  }catch(Exception ignored){return List.of();}
 }
 String generate(String question,String context,Map<String,Object> decisions,List<SourceRef> refs){
  if(base!=null&&!base.isBlank()&&key!=null&&!key.isBlank()) try{
   var body=Map.of("model",model,"temperature",0.1,"messages",List.of(
    Map.of("role","system","content","Answer only from supplied organization document excerpts. If the excerpts do not contain the answer, say so. Never invent policy. Cite document and page names. Do not reveal hidden reasoning."),
    Map.of("role","user","content","Question: "+question+"\nStructured decisions: "+decisions+"\nDocument excerpts:\n"+context)));
   var client=java.net.http.HttpClient.newHttpClient();var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base.replaceAll("/+$","")+"/chat/completions")).header("Authorization","Bearer "+key).header("Content-Type","application/json").POST(java.net.http.HttpRequest.BodyPublishers.ofString(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body))).build();
   var response=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
   if(response.statusCode()<200||response.statusCode()>=300) throw new IllegalStateException("LLM request failed"); var node=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.body());return node.path("choices").path(0).path("message").path("content").asText();
  }catch(Exception ignored){}
  StringBuilder out=new StringBuilder("Based on the uploaded documents, the most relevant information is:\n\n");
  out.append(context.length()>1800?context.substring(0,1800)+"…":context);
  out.append("\n\nThis response is grounded in the retrieved excerpts. Please verify the cited policy text before making a final decision.");
  return out.toString();
 }
}
