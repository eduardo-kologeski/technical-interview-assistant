package com.kologeski.interviewassistant.interviewkit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.kologeski.interviewassistant.audit.*;
import com.kologeski.interviewassistant.api.ResourceNotFoundException;
import com.kologeski.interviewassistant.company.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class InterviewKitService {
    private final JdbcClient jdbc;
    private final CompanyAuthorizationPolicy authorization;
    private final CurrentActor actor;
    private final AuditTrailService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public InterviewKitService(JdbcClient jdbc, CompanyAuthorizationPolicy authorization, CurrentActor actor,
            AuditTrailService audit, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc; this.authorization = authorization; this.actor = actor; this.audit = audit;
        this.json = json; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public KitView get(UUID vacancyId) {
        UUID companyId = authorization.require(CompanyCapability.VIEW_VACANCIES).companyId();
        KitRow kit = jdbc.sql("select id, name from interview_kits where vacancy_id=:vacancy and company_id=:company")
                .param("vacancy", vacancyId).param("company", companyId)
                .query((rs, n) -> new KitRow(rs.getObject("id", UUID.class), rs.getString("name")))
                .optional().orElseThrow(() -> new ResourceNotFoundException("Kit de entrevista não encontrado."));
        List<VersionView> versions = jdbc.sql("""
                select id, version_number, status, published_at from interview_kit_versions
                where kit_id=:kit order by version_number desc
                """).param("kit", kit.id()).query((rs, n) -> version(rs.getObject("id", UUID.class),
                        rs.getInt("version_number"), rs.getString("status"),
                        rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant())).list();
        return new KitView(kit.id(), vacancyId, kit.name(), versions);
    }

    @Transactional
    public VersionView saveDraft(UUID vacancyId, String name, List<ItemInput> items) {
        var active = authorization.require(CompanyCapability.MANAGE_VACANCIES);
        if (items == null || items.isEmpty() || items.size() > 100) throw new IllegalArgumentException("O kit deve possuir de 1 a 100 critérios.");
        KitView current = get(vacancyId);
        UUID versionId = current.versions().stream().filter(v -> v.status().equals("DRAFT"))
                .map(VersionView::id).findFirst().orElse(null);
        if (versionId == null) {
            versionId = UUID.randomUUID();
            int next = current.versions().stream().mapToInt(VersionView::version).max().orElse(0) + 1;
            jdbc.sql("insert into interview_kit_versions(id,kit_id,version_number,status,created_at,created_by) values(:id,:kit,:version,'DRAFT',:now,:actor)")
                    .param("id", versionId).param("kit", current.id()).param("version", next)
                    .param("now", Instant.now(clock)).param("actor", actor.required().id()).update();
        } else jdbc.sql("delete from interview_kit_items where version_id=:id").param("id", versionId).update();
        jdbc.sql("update interview_kits set name=:name where id=:id and company_id=:company")
                .param("name", required(name, "nome")).param("id", current.id()).param("company", active.companyId()).update();
        int position = 0;
        for (ItemInput item : items) jdbc.sql("""
                insert into interview_kit_items(version_id,position,criterion,question,weight,rubric)
                values(:version,:position,:criterion,:question,:weight,:rubric)
                """).param("version", versionId).param("position", ++position)
                .param("criterion", required(item.criterion(), "critério"))
                .param("question", required(item.question(), "pergunta"))
                .param("weight", validWeight(item.weight())).param("rubric", required(item.rubric(), "rubrica")).update();
        return version(versionId);
    }

    @Transactional
    public VersionView publish(UUID vacancyId, UUID versionId) {
        var active = authorization.require(CompanyCapability.MANAGE_VACANCIES);
        KitView kit = get(vacancyId);
        VersionView draft = kit.versions().stream().filter(v -> v.id().equals(versionId) && v.status().equals("DRAFT"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Somente uma versão draft pode ser publicada."));
        if (draft.items().isEmpty()) throw new IllegalStateException("Não é possível publicar um kit vazio.");
        jdbc.sql("update interview_kit_versions set status='ARCHIVED' where kit_id=:kit and status='PUBLISHED'")
                .param("kit", kit.id()).update();
        jdbc.sql("update interview_kit_versions set status='PUBLISHED', published_at=:now where id=:id")
                .param("now", Instant.now(clock)).param("id", versionId).update();
        audit.record(active.companyId(), active.userId(), "DOMAIN", "KIT_PUBLISHED", "INTERVIEW_KIT_VERSION", versionId);
        return version(versionId);
    }

    @Transactional
    public void initialize(UUID vacancyId, UUID companyId, String name, List<String> requirements, String actorId) {
        UUID kitId = vacancyId;
        Instant now = Instant.now(clock);
        jdbc.sql("insert into interview_kits(id,company_id,vacancy_id,name,created_at,created_by) values(:id,:company,:vacancy,:name,:now,:actor)")
                .param("id", kitId).param("company", companyId).param("vacancy", vacancyId)
                .param("name", name + " — Kit legado").param("now", now).param("actor", actorId).update();
        jdbc.sql("insert into interview_kit_versions(id,kit_id,version_number,status,published_at,created_at,created_by) values(:id,:kit,1,'PUBLISHED',:now,:now,:actor)")
                .param("id", kitId).param("kit", kitId).param("now", now).param("actor", actorId).update();
        int position = 0;
        for (String requirement : requirements) jdbc.sql("insert into interview_kit_items(version_id,position,criterion,question,weight,rubric) values(:version,:position,:criterion,:question,3,:rubric)")
                .param("version", kitId).param("position", ++position).param("criterion", requirement)
                .param("question", "Avalie evidências práticas sobre " + requirement + ".")
                .param("rubric", "0: sem evidência; 3: atende ao esperado; 5: domínio avançado.").update();
    }

    @Transactional(readOnly = true)
    public String publishedSnapshot(UUID vacancyId, UUID companyId) {
        VersionView published = jdbc.sql("""
                select v.id from interview_kit_versions v join interview_kits k on k.id=v.kit_id
                where k.vacancy_id=:vacancy and k.company_id=:company and v.status='PUBLISHED'
                """).param("vacancy", vacancyId).param("company", companyId).query(UUID.class).optional()
                .map(this::version).orElse(null);
        if (published == null) return "{}";
        try { return json.writeValueAsString(published); }
        catch (JacksonException error) { throw new IllegalStateException("Não foi possível criar snapshot do kit.", error); }
    }

    private VersionView version(UUID id) {
        return jdbc.sql("select version_number,status,published_at from interview_kit_versions where id=:id")
                .param("id", id).query((rs, n) -> version(id, rs.getInt("version_number"), rs.getString("status"),
                        rs.getTimestamp("published_at") == null ? null : rs.getTimestamp("published_at").toInstant()))
                .optional().orElseThrow(() -> new ResourceNotFoundException("Versão do kit não encontrada."));
    }
    private VersionView version(UUID id, int number, String status, Instant publishedAt) {
        List<ItemView> items = jdbc.sql("select position,criterion,question,weight,rubric from interview_kit_items where version_id=:id order by position")
                .param("id", id).query((rs, n) -> new ItemView(rs.getInt("position"), rs.getString("criterion"),
                        rs.getString("question"), rs.getInt("weight"), rs.getString("rubric"))).list();
        return new VersionView(id, number, status, publishedAt, items);
    }
    private static String required(String value, String field) { if (value == null || value.trim().isBlank()) throw new IllegalArgumentException("Informe " + field + "."); return value.trim(); }
    private static int validWeight(int value) { if (value < 1 || value > 10) throw new IllegalArgumentException("O peso deve estar entre 1 e 10."); return value; }
    private record KitRow(UUID id, String name) { }
    public record KitView(UUID id, UUID vacancyId, String name, List<VersionView> versions) { }
    public record VersionView(UUID id, int version, String status, Instant publishedAt, List<ItemView> items) { }
    public record ItemView(int position, String criterion, String question, int weight, String rubric) { }
    public record ItemInput(String criterion, String question, int weight, String rubric) { }
}
