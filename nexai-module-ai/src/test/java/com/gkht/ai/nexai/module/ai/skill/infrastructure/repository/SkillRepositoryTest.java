package com.gkht.ai.nexai.module.ai.skill.infrastructure.repository;

import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillOwnerLevel;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillVersion;
import com.gkht.ai.nexai.module.ai.skill.domain.repository.SkillRepository;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.converter.SkillConverterImpl;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillResourceMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillVersionMapper;
import com.gkht.ai.nexai.module.ai.support.TenantDbTestConfiguration;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skill 仓储双表读写契约（H2）：版本内容拆 markdown 列 + ai_skill_resources 行后的
 * 全量往返、精确取版本、级联删除三表清理。
 */
@Import({SkillRepositoryImpl.class, SkillConverterImpl.class, SkillMapper.class,
        SkillVersionMapper.class, SkillResourceMapper.class, TenantDbTestConfiguration.class})
public class SkillRepositoryTest extends BaseDbUnitTest {

    private static final long TENANT_ONE = 1L;

    @Resource
    private SkillRepository skillRepository;

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(TENANT_ONE);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("saveVersion 后 findVersion/listVersions 全量往返（markdown + 资源）")
    void versionRoundTripWithResources() {
        Skill skill = Skill.create("data-clean", "数据清洗能力包", SkillOwnerLevel.TENANT, null);
        skillRepository.save(skill);
        SkillContent content = SkillContent.of(
                "---\nname: data-clean\ndescription: 清洗\n---\n# 清洗",
                Map.of("rules/a.md", "规则A", "scripts/run.sh", "base64:AAAA"));
        skillRepository.saveVersion(SkillVersion.create(skill.getId(), 1, content, "首版"));

        SkillVersion found = skillRepository.findVersion(skill.getId(), 1);
        assertNotNull(found);
        assertEquals(content.getMarkdown(), found.getContent().getMarkdown());
        assertEquals(Map.of("rules/a.md", "规则A", "scripts/run.sh", "base64:AAAA"),
                found.getContent().getResources());

        var versions = skillRepository.listVersions(skill.getId());
        assertEquals(1, versions.size());
        assertEquals(2, versions.get(0).getContent().getResources().size(),
                "listVersions 保持含资源语义（AgentRuntimeAssembler 依赖）");
    }

    @Test
    @DisplayName("findVersion 不存在返回 null")
    void findVersionMiss() {
        assertNull(skillRepository.findVersion(999L, 1));
    }

    @Test
    @DisplayName("deleteByIdCascade 三表清理（skill/version/resources）")
    void deleteCascadesThreeTables() {
        Skill skill = Skill.create("data-clean", "数据清洗能力包", SkillOwnerLevel.TENANT, null);
        skillRepository.save(skill);
        skillRepository.saveVersion(SkillVersion.create(skill.getId(), 1,
                SkillContent.of("---\nname: data-clean\ndescription: 清洗\n---\n# 清洗",
                        Map.of("rules/a.md", "规则A")), "首版"));

        skillRepository.deleteByIdCascade(skill.getId());
        assertTrue(skillRepository.listVersions(skill.getId()).isEmpty());
        assertNull(skillRepository.findVersion(skill.getId(), 1));
    }
}
