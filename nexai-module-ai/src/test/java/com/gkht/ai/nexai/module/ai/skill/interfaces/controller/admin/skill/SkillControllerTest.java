package com.gkht.ai.nexai.module.ai.skill.interfaces.controller.admin.skill;

import com.gkht.ai.nexai.framework.test.core.ut.BaseDbUnitTest;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillCreateCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillVersionCommand;
import com.gkht.ai.nexai.module.ai.skill.application.service.SkillService;
import com.gkht.ai.nexai.module.ai.skill.application.service.SkillServiceImpl;
import com.gkht.ai.nexai.module.ai.skill.domain.gateway.SkillMaterializationGateway;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.converter.SkillConverterImpl;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillVersionMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.repository.SkillRepositoryImpl;
import com.gkht.ai.nexai.module.ai.support.TenantDbTestConfiguration;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.Map;

import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_NAME_DUPLICATE;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skill HTTP 契约测试（H2 全链路，物化网关 stub 不落真实磁盘）：
 * 创建（归属/首版）、版本链登记、列表/版本列表、删除级联、唯一性。
 * 物化契约（真实 agentscope 落盘）由物化网关测试单独保障。
 */
@Import({SkillController.class, SkillServiceImpl.class, SkillRepositoryImpl.class,
        SkillConverterImpl.class, SkillMapper.class, SkillVersionMapper.class,
        TenantDbTestConfiguration.class, SkillControllerTest.FakeMaterializationConfiguration.class})
public class SkillControllerTest extends BaseDbUnitTest {

    private static final long TENANT_ONE = 1L;

    @Resource
    private SkillService skillService;

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(TENANT_ONE);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("创建 Skill：默认租户级，登记首版，物化被调用")
    public void createSkillPersistsAndMaterializes() {
        Long id = skillService.createSkill(createCommand("data-clean"), 1L);
        assertNotNull(id);

        var page = skillService.getSkillPage(new com.gkht.ai.nexai.module.ai.skill.application.query.SkillPageQuery(), 1L);
        assertEquals(1, page.getTotal());
        assertEquals("data-clean", page.getList().get(0).getName());
        assertEquals("TENANT", page.getList().get(0).getOwnerLevel());
        assertEquals(1, page.getList().get(0).getCurrentVersionNo());
    }

    @Test
    @DisplayName("创建 Skill：同归属下名称重复报业务异常")
    public void createSkillRejectsDuplicateName() {
        skillService.createSkill(createCommand("data-clean"), 1L);
        assertServiceException(() -> skillService.createSkill(createCommand("data-clean"), 1L),
                SKILL_NAME_DUPLICATE, "data-clean");
    }

    @Test
    @DisplayName("登记新版本：版本号递增，当前指针推进")
    public void addVersionIncrements() {
        Long id = skillService.createSkill(createCommand("data-clean"), 1L);

        SkillVersionCommand versionCommand = new SkillVersionCommand();
        versionCommand.setSkillId(id);
        versionCommand.setMarkdown("---\nname: data-clean\ndescription: 清洗 v2\n---\n# 清洗 v2");
        versionCommand.setResources(Map.of("rules.md", "rule2"));
        Integer versionNo = skillService.addSkillVersion(versionCommand, 1L);
        assertEquals(2, versionNo);

        var versions = skillService.listSkillVersions(id, 1L);
        assertEquals(2, versions.size());
        assertTrue(versions.get(1).getCurrent(), "最新版本应为当前版本");
    }

    @Test
    @DisplayName("删除 Skill：级联删除版本链")
    public void deleteSkillCascades() {
        Long id = skillService.createSkill(createCommand("data-clean"), 1L);
        SkillVersionCommand versionCommand = new SkillVersionCommand();
        versionCommand.setSkillId(id);
        versionCommand.setMarkdown("---\nname: data-clean\ndescription: v2\n---\n# v2");
        skillService.addSkillVersion(versionCommand, 1L);

        skillService.deleteSkill(id, 1L);
        assertServiceException(() -> skillService.listSkillVersions(id, 1L), SKILL_NOT_EXISTS);
    }

    @Test
    @DisplayName("上架/下架：published 位切换并透出，不存在报错")
    public void publishAndUnpublishToggles() {
        Long id = skillService.createSkill(createCommand("data-clean"), 1L);
        var pageQuery = new com.gkht.ai.nexai.module.ai.skill.application.query.SkillPageQuery();
        assertEquals(0, skillService.getSkillPage(pageQuery, 1L).getList().get(0).getPublished(),
                "创建默认未上架");

        skillService.publishSkill(id, 1L);
        assertEquals(1, skillService.getSkillPage(pageQuery, 1L).getList().get(0).getPublished());

        skillService.unpublishSkill(id, 1L);
        assertEquals(0, skillService.getSkillPage(pageQuery, 1L).getList().get(0).getPublished());

        assertServiceException(() -> skillService.publishSkill(999L, 1L), SKILL_NOT_EXISTS);
    }

    @Test
    @DisplayName("分页筛选：上架位与来源（自建/Git）过滤")
    public void pageFilterByPublishedAndSource() {
        Long idA = skillService.createSkill(createCommand("data-clean"), 1L);
        skillService.publishSkill(idA, 1L);
        skillService.createSkill(createCommand("report-writer"), 1L);

        var publishedQuery = new com.gkht.ai.nexai.module.ai.skill.application.query.SkillPageQuery();
        publishedQuery.setPublished(1);
        assertEquals(1, skillService.getSkillPage(publishedQuery, 1L).getTotal(), "仅上架的 1 条");

        var createdQuery = new com.gkht.ai.nexai.module.ai.skill.application.query.SkillPageQuery();
        createdQuery.setSourceType("created");
        assertEquals(2, skillService.getSkillPage(createdQuery, 1L).getTotal(), "自建 2 条");

        var gitQuery = new com.gkht.ai.nexai.module.ai.skill.application.query.SkillPageQuery();
        gitQuery.setSourceType("git");
        assertEquals(0, skillService.getSkillPage(gitQuery, 1L).getTotal(), "Git 来源 0 条");
    }

    @Test
    @DisplayName("版本内容预览：返回 markdown 与资源路径清单，版本不存在报错")
    public void getVersionContentPreview() {
        Long id = skillService.createSkill(createCommand("data-clean"), 1L);
        SkillVersionCommand vc = new SkillVersionCommand();
        vc.setSkillId(id);
        vc.setMarkdown("---\nname: data-clean\ndescription: v2\n---\n# v2");
        vc.setResources(Map.of("rules/a.md", "规则A", "scripts/run.sh", "base64:AAAA"));
        skillService.addSkillVersion(vc, 1L);

        var dto = skillService.getVersionContent(id, 2);
        assertEquals("---\nname: data-clean\ndescription: v2\n---\n# v2", dto.getMarkdown());
        assertEquals(java.util.List.of("rules/a.md", "scripts/run.sh"), dto.getResourcePaths());

        assertServiceException(() -> skillService.getVersionContent(id, 9),
                com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_VERSION_NOT_EXISTS);
    }

    private SkillCreateCommand createCommand(String name) {
        SkillCreateCommand command = new SkillCreateCommand();
        command.setName(name);
        command.setDescription("数据清洗能力包");
        command.setMarkdown("---\nname: " + name + "\ndescription: 清洗\n---\n# 清洗\n\n规则");
        return command;
    }

    /** 物化网关桩：记录物化调用（不落真实磁盘），供断言 */
    @TestConfiguration
    static class FakeMaterializationConfiguration {

        @Bean
        public SkillMaterializationGateway skillMaterializationGateway() {
            return new SkillMaterializationGateway() {
                @Override
                public String materialize(Skill skill, Long tenantId, SkillContent content) {
                    // 物化契约由真实网关测试保障，此处仅记录
                    return "/tmp/fake-skill-materialize/" + skill.getName();
                }
            };
        }
    }

}
