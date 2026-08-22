package com.gkht.ai.nexai.module.ai.skill.interfaces.controller.admin.skill;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.gkht.ai.nexai.framework.common.pojo.PageResult;
import com.gkht.ai.nexai.framework.mybatis.core.util.MyBatisUtils;
import com.gkht.ai.nexai.framework.tenant.config.TenantProperties;
import com.gkht.ai.nexai.framework.tenant.core.context.TenantContextHolder;
import com.gkht.ai.nexai.framework.tenant.core.db.TenantDatabaseInterceptor;
import com.gkht.ai.nexai.framework.test.core.ut.BaseDbAndRedisUnitTest;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillCreateCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillPublishCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillSwitchVersionCommand;
import com.gkht.ai.nexai.module.ai.skill.application.command.SkillUpdateCommand;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDetailDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillDTO;
import com.gkht.ai.nexai.module.ai.skill.application.dto.SkillVersionDTO;
import com.gkht.ai.nexai.module.ai.skill.application.query.SkillPageQuery;
import com.gkht.ai.nexai.module.ai.skill.application.service.SkillServiceImpl;
import com.gkht.ai.nexai.module.ai.skill.domain.gateway.SkillMdParserGateway;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.converter.SkillConverterImpl;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.gateway.AgentscopeSkillMdParserGateway;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.gateway.AgentscopeSkillRepository;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.mapper.SkillVersionMapper;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.repository.SkillRepositoryImpl;
import io.agentscope.core.skill.AgentSkill;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static com.gkht.ai.nexai.framework.common.exception.enums.GlobalErrorCodeConstants.SUCCESS;
import static com.gkht.ai.nexai.framework.test.core.util.AssertUtils.assertServiceException;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_MD_INVALID;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_NAME_DUPLICATE;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_NOT_EXISTS;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_PUBLISH_WITHOUT_DRAFT;
import static com.gkht.ai.nexai.module.ai.enums.ErrorCodeConstants.SKILL_VERSION_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 技能 S1 接缝测试：直接调用 /admin-api/ai/skill/** 对应的控制器方法，
 * 走真实 controller → application → repository → H2 全链路，只断言外部行为。
 * 官方 AgentSkillRepository 实现（AgentscopeSkillRepository）的行为与租户隔离同链路验证。
 */
@Import({SkillController.class, SkillServiceImpl.class, SkillRepositoryImpl.class,
        SkillConverterImpl.class, AgentscopeSkillMdParserGateway.class, AgentscopeSkillRepository.class,
        SkillControllerTest.TenantDbTestConfiguration.class})
public class SkillControllerTest extends BaseDbAndRedisUnitTest {

    private static final String VALID_MD =
            "---\nname: pdf-report\ndescription: 生成 PDF 汇报文档\n---\n按模板生成汇报文档。";

    @Resource
    private SkillController skillController;

    @Resource
    private AgentscopeSkillRepository agentscopeSkillRepository;

    @Resource
    private SkillMapper skillMapper;

    /** 注入以强制初始化租户拦截器 bean（向 MybatisPlusInterceptor 注册 inner） */
    @Resource
    private TenantLineInnerInterceptor tenantLineInnerInterceptor;

    @TestConfiguration
    @EnableConfigurationProperties(TenantProperties.class)
    static class TenantDbTestConfiguration {

        @Bean
        public TenantLineInnerInterceptor tenantLineInnerInterceptor(TenantProperties properties,
                                                                     MybatisPlusInterceptor interceptor) {
            TenantLineInnerInterceptor inner =
                    new TenantLineInnerInterceptor(new TenantDatabaseInterceptor(properties));
            MyBatisUtils.addInterceptor(interceptor, inner, 0);
            return inner;
        }

    }

    /** 解析网关按生产实现装配（AgentscopeSkillMdParserGateway），SKILL.md 校验走官方解析器 */

    @BeforeEach
    public void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    public void tearDown() {
        TenantContextHolder.clear();
    }

    private SkillCreateCommand createCommand(String skillMd) {
        SkillCreateCommand command = new SkillCreateCommand();
        command.setSkillMd(skillMd);
        command.setResources(Map.of("scripts/run.py", "print('hi')"));
        return command;
    }

    /** 创建 + 发布一个技能，返回技能编号 */
    private Long createPublishedSkill(String name, String description) {
        Long skillId = skillController.createSkill(createCommand(skillMdOf(name, description))).getData();
        skillController.publishSkill(publishCommand(skillId, "v1"));
        return skillId;
    }

    private String skillMdOf(String name, String description) {
        return "---\nname: " + name + "\ndescription: " + description + "\n---\n技能说明正文。";
    }

    private SkillPublishCommand publishCommand(Long skillId, String remark) {
        SkillPublishCommand command = new SkillPublishCommand();
        command.setId(skillId);
        command.setRemark(remark);
        return command;
    }

    // ==================== 管理面行为 ====================

    @Test
    @DisplayName("创建技能：SKILL.md 原文与资源落草稿、name/description 从 front matter 解析冗余、租户自动归属")
    public void createSkillPersistsDraft() {
        Long skillId = skillController.createSkill(createCommand(VALID_MD)).getData();

        assertNotNull(skillId);
        var dataObject = skillMapper.selectById(skillId);
        assertEquals(VALID_MD, dataObject.getDraftSkillMd());
        assertTrue(dataObject.getDraftResources().contains("scripts/run.py"));
        assertEquals("pdf-report", dataObject.getName());
        assertEquals("生成 PDF 汇报文档", dataObject.getDescription());
        assertEquals(0, dataObject.getLatestVersionNo());
        assertNull(dataObject.getCurrentVersionNo());
        assertEquals(1L, dataObject.getTenantId());
    }

    @Test
    @DisplayName("SKILL.md 校验：缺 name/description、无 front matter、无正文均以明确错误拒绝")
    public void createSkillInvalidMd() {
        // 缺 name
        SkillCreateCommand missingName = createCommand("---\ndescription: 只有描述\n---\n正文。");
        assertServiceException(() -> skillController.createSkill(missingName), SKILL_MD_INVALID,
                "The SKILL.md must have a YAML Front Matter including `name` and `description` fields.");
        // 缺 description
        SkillCreateCommand missingDescription = createCommand("---\nname: pdf-report\n---\n正文。");
        assertServiceException(() -> skillController.createSkill(missingDescription), SKILL_MD_INVALID,
                "The SKILL.md must have a YAML Front Matter including `name` and `description` fields.");
        // 无 front matter（整体按缺字段拒绝）
        assertServiceException(() -> skillController.createSkill(createCommand("只有正文没有 front matter。")),
                SKILL_MD_INVALID,
                "The SKILL.md must have a YAML Front Matter including `name` and `description` fields.");
        // 无正文
        assertServiceException(() -> skillController.createSkill(createCommand(
                        "---\nname: pdf-report\ndescription: 生成 PDF 汇报文档\n---\n")),
                SKILL_MD_INVALID,
                "The SKILL.md must have content except for the YAML Front Matter.");
    }

    @Test
    @DisplayName("租户内技能名唯一：同名创建拒绝；编辑改成他人在用名拒绝；软删后名字可复用")
    public void skillNameUniqueness() {
        skillController.createSkill(createCommand(VALID_MD));

        assertServiceException(() -> skillController.createSkill(createCommand(VALID_MD)),
                SKILL_NAME_DUPLICATE, "pdf-report");

        // 另一个技能（不同名）编辑时改成 pdf-report → 拒绝
        Long otherId = skillController.createSkill(createCommand(
                "---\nname: word-report\ndescription: 生成 Word 文档\n---\n正文。")).getData();
        SkillUpdateCommand rename = new SkillUpdateCommand();
        rename.setId(otherId);
        rename.setSkillMd(VALID_MD);
        assertServiceException(() -> skillController.updateSkill(rename), SKILL_NAME_DUPLICATE, "pdf-report");

        // 删除后名字可复用
        skillController.deleteSkill(skillMapper.selectByName("pdf-report").getId());
        assertNotNull(skillController.createSkill(createCommand(VALID_MD)).getData());
    }

    @Test
    @DisplayName("编辑技能：SKILL.md 全文覆盖草稿，name/description 随 front matter 更新（改名生效）")
    public void updateSkillOverwritesDraft() {
        Long skillId = skillController.createSkill(createCommand(VALID_MD)).getData();

        String updatedMd = "---\nname: pdf-report-v2\ndescription: 生成新版汇报文档\n---\n按新模板生成。";
        SkillUpdateCommand command = new SkillUpdateCommand();
        command.setId(skillId);
        command.setSkillMd(updatedMd);
        command.setResources(Map.of("scripts/run.py", "print('v2')"));
        skillController.updateSkill(command);

        SkillDetailDTO detail = skillController.getSkill(skillId).getData();
        assertEquals("pdf-report-v2", detail.getName());
        assertEquals("生成新版汇报文档", detail.getDescription());
        assertEquals(updatedMd, detail.getDraft().getSkillMd());
        assertEquals("print('v2')", detail.getDraft().getResources().get("scripts/run.py"));
    }

    @Test
    @DisplayName("发布：草稿固化为 v1 快照、指针前移、草稿清空；无草稿再发布报错；v2 后 v1 快照原样")
    public void publishLocksImmutableSnapshot() {
        Long skillId = skillController.createSkill(createCommand(VALID_MD)).getData();

        assertEquals(1, skillController.publishSkill(publishCommand(skillId, "首个版本")).getData());

        SkillDetailDTO detail = skillController.getSkill(skillId).getData();
        assertEquals(1, detail.getCurrentVersionNo());
        assertFalse(detail.getHasDraft());
        assertEquals(VALID_MD, detail.getCurrentVersion().getContent().getSkillMd());
        assertTrue(detail.getCurrentVersion().getContent().getResources().containsKey("scripts/run.py"));
        assertEquals("首个版本", detail.getCurrentVersion().getRemark());

        // 再编辑 → 发布 v2；v1 快照保持发布时内容
        String v2Md = "---\nname: pdf-report\ndescription: 生成 PDF 汇报文档\n---\n按新版模板生成。";
        SkillUpdateCommand command = new SkillUpdateCommand();
        command.setId(skillId);
        command.setSkillMd(v2Md);
        skillController.updateSkill(command);
        assertEquals(2, skillController.publishSkill(publishCommand(skillId, "v2")).getData());

        List<SkillVersionDTO> versions = skillController.getVersionList(skillId).getData();
        assertEquals(2, versions.size());
        assertEquals(v2Md, versions.get(0).getContent().getSkillMd());
        assertEquals(VALID_MD, versions.get(1).getContent().getSkillMd());

        assertServiceException(() -> skillController.publishSkill(publishCommand(skillId, null)),
                SKILL_PUBLISH_WITHOUT_DRAFT);
    }

    @Test
    @DisplayName("切换默认版本：可切回历史版本，越界版本号报错")
    public void switchDefaultVersion() {
        Long skillId = createPublishedSkill("pdf-report", "生成 PDF 汇报文档");
        String v2Md = "---\nname: pdf-report\ndescription: 生成 PDF 汇报文档\n---\nv2 正文。";
        SkillUpdateCommand command = new SkillUpdateCommand();
        command.setId(skillId);
        command.setSkillMd(v2Md);
        skillController.updateSkill(command);
        skillController.publishSkill(publishCommand(skillId, "v2"));

        SkillSwitchVersionCommand rollback = new SkillSwitchVersionCommand();
        rollback.setId(skillId);
        rollback.setVersionNo(1);
        assertEquals(SUCCESS.getCode(), skillController.switchDefaultVersion(rollback).getCode());
        assertEquals(skillMdOf("pdf-report", "生成 PDF 汇报文档"),
                skillController.getSkill(skillId).getData().getCurrentVersion().getContent().getSkillMd());

        SkillSwitchVersionCommand invalid = new SkillSwitchVersionCommand();
        invalid.setId(skillId);
        invalid.setVersionNo(3);
        assertServiceException(() -> skillController.switchDefaultVersion(invalid), SKILL_VERSION_NOT_EXISTS);
    }

    @Test
    @DisplayName("分页按名称过滤；删除级联（技能与版本均逻辑删除）；不存在技能报错")
    public void pageDeleteAndRequireExists() {
        Long skillId = createPublishedSkill("pdf-report", "生成 PDF 汇报文档");

        SkillPageQuery query = new SkillPageQuery();
        query.setName("pdf");
        PageResult<SkillDTO> page = skillController.getSkillPage(query).getData();
        assertEquals(1, page.getTotal());
        assertEquals("pdf-report", page.getList().get(0).getName());
        assertFalse(page.getList().get(0).getHasDraft());

        skillController.deleteSkill(skillId);
        assertEquals(0, skillController.getSkillPage(new SkillPageQuery()).getData().getTotal());
        assertServiceException(() -> skillController.getSkill(skillId), SKILL_NOT_EXISTS);
    }

    @Test
    @DisplayName("租户隔离：租户 1 的技能对租户 2 不可见（管理面分页）")
    public void tenantIsolationOnAdminPage() {
        createPublishedSkill("pdf-report", "生成 PDF 汇报文档");

        TenantContextHolder.setTenantId(2L);
        assertEquals(0, skillController.getSkillPage(new SkillPageQuery()).getData().getTotal());

        TenantContextHolder.setTenantId(1L);
        assertEquals(1, skillController.getSkillPage(new SkillPageQuery()).getData().getTotal());
    }

    // ==================== 官方 AgentSkillRepository 实现行为 ====================

    @Test
    @DisplayName("运行时仓储：未发布技能不可见；发布后 getSkill 按名重建 AgentSkill（含资源与 source）")
    public void repositoryVisibilityRequiresPublish() {
        Long skillId = skillController.createSkill(createCommand(VALID_MD)).getData();

        // 未发布：读侧不可见
        assertTrue(agentscopeSkillRepository.getAllSkillNames().isEmpty());
        assertFalse(agentscopeSkillRepository.skillExists("pdf-report"));
        assertThrows(IllegalArgumentException.class, () -> agentscopeSkillRepository.getSkill("pdf-report"));

        skillController.publishSkill(publishCommand(skillId, "v1"));

        AgentSkill agentSkill = agentscopeSkillRepository.getSkill("pdf-report");
        assertEquals("pdf-report", agentSkill.getName());
        assertEquals("生成 PDF 汇报文档", agentSkill.getDescription());
        assertEquals("按模板生成汇报文档。", agentSkill.getSkillContent());
        assertEquals("print('hi')", agentSkill.getResource("scripts/run.py"));
        assertEquals("nexai", agentSkill.getSource());
        assertTrue(agentscopeSkillRepository.skillExists("pdf-report"));
        assertEquals(List.of("pdf-report"), agentscopeSkillRepository.getAllSkillNames());
        assertEquals(1, agentscopeSkillRepository.getAllSkills().size());
    }

    @Test
    @DisplayName("运行时仓储：读侧随默认版本切换回滚（v2 → 切回 v1 读到 v1 快照）")
    public void repositoryReadsCurrentDefaultVersion() {
        Long skillId = createPublishedSkill("pdf-report", "生成 PDF 汇报文档");
        String v2Md = "---\nname: pdf-report\ndescription: 生成 PDF 汇报文档\n---\nv2 正文。";
        SkillUpdateCommand command = new SkillUpdateCommand();
        command.setId(skillId);
        command.setSkillMd(v2Md);
        skillController.updateSkill(command);
        skillController.publishSkill(publishCommand(skillId, "v2"));
        assertEquals("v2 正文。", agentscopeSkillRepository.getSkill("pdf-report").getSkillContent());

        SkillSwitchVersionCommand rollback = new SkillSwitchVersionCommand();
        rollback.setId(skillId);
        rollback.setVersionNo(1);
        skillController.switchDefaultVersion(rollback);
        assertEquals("技能说明正文。", agentscopeSkillRepository.getSkill("pdf-report").getSkillContent());
    }

    @Test
    @DisplayName("运行时仓储 save：新技能写入草稿（不发布不进读侧）；force=false 已存在抛冲突；force=true 覆盖草稿")
    public void repositorySaveWritesDraft() {
        // 新技能：save 创建技能 + 草稿，读侧仍不可见（发布才可见）
        AgentSkill fresh = new AgentSkill("word-report", "生成 Word 文档", "按模板生成 Word。", Map.of());
        assertTrue(agentscopeSkillRepository.save(List.of(fresh), false));
        assertFalse(agentscopeSkillRepository.skillExists("word-report"));
        assertNotNull(skillMapper.selectByName("word-report"));

        // 已存在 + force=false → 显式冲突（对齐官方语义）
        AgentSkill updated = new AgentSkill("word-report", "生成 Word 文档 v2", "新正文。", Map.of());
        assertThrows(IllegalStateException.class, () -> agentscopeSkillRepository.save(List.of(updated), false));

        // force=true 覆盖草稿；发布后读侧按草稿内容可见
        assertTrue(agentscopeSkillRepository.save(List.of(updated), true));
        Long skillId = skillMapper.selectByName("word-report").getId();
        skillController.publishSkill(publishCommand(skillId, null));
        AgentSkill loaded = agentscopeSkillRepository.getSkill("word-report");
        assertEquals("生成 Word 文档 v2", loaded.getDescription());
        assertEquals("新正文。", loaded.getSkillContent());
    }

    @Test
    @DisplayName("运行时仓储 delete：删除后读侧不可见；只读旗标挡写通道（save/delete 返回 false，读不受限）")
    public void repositoryDeleteAndWriteableFlag() {
        createPublishedSkill("pdf-report", "生成 PDF 汇报文档");
        assertTrue(agentscopeSkillRepository.isWriteable());

        agentscopeSkillRepository.setWriteable(false);
        AgentSkill anySkill = new AgentSkill("x", "y", "z", Map.of());
        assertFalse(agentscopeSkillRepository.save(List.of(anySkill), true));
        assertFalse(agentscopeSkillRepository.delete("pdf-report"));
        // 读侧不受旗标限制
        assertTrue(agentscopeSkillRepository.skillExists("pdf-report"));
        assertFalse(agentscopeSkillRepository.getRepositoryInfo().isWritable());

        agentscopeSkillRepository.setWriteable(true);
        assertTrue(agentscopeSkillRepository.delete("pdf-report"));
        assertFalse(agentscopeSkillRepository.skillExists("pdf-report"));
        assertNull(skillMapper.selectByName("pdf-report"));
    }

    @Test
    @DisplayName("运行时仓储：getSource 为 类型_定位 格式；租户 2 读不到租户 1 的技能；无租户上下文即报错")
    public void repositorySourceAndTenantIsolation() {
        createPublishedSkill("pdf-report", "生成 PDF 汇报文档");
        assertEquals("nexai", agentscopeSkillRepository.getRepositoryInfo().getType());
        assertEquals("ai_skill", agentscopeSkillRepository.getRepositoryInfo().getLocation());
        assertEquals("nexai_ai_skill", agentscopeSkillRepository.getSource());

        TenantContextHolder.setTenantId(2L);
        assertTrue(agentscopeSkillRepository.getAllSkillNames().isEmpty());
        assertTrue(agentscopeSkillRepository.getAllSkills().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> agentscopeSkillRepository.getSkill("pdf-report"));

        // 读侧强断言租户上下文：调用方（运行时链路）必须先建立租户
        TenantContextHolder.clear();
        assertThrows(NullPointerException.class, () -> agentscopeSkillRepository.getAllSkillNames());
    }

}
