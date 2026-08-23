package com.gkht.ai.nexai.module.ai.skill.infrastructure.gateway;

import com.gkht.ai.nexai.module.ai.framework.config.AiRuntimeProperties;
import com.gkht.ai.nexai.module.ai.skill.domain.model.Skill;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillContent;
import com.gkht.ai.nexai.module.ai.skill.domain.model.SkillOwnerLevel;
import com.gkht.ai.nexai.module.ai.skill.infrastructure.gateway.AgentscopeSkillMaterializationGateway;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skill 物化契约测试（agentscope 直用，落临时目录）：
 * DB 版本内容 → 文件目录（SKILL.md + 资源文件），agentscope 文件仓库源可发现；
 * 内容比对跳过覆写（DB 唯一权威源）。
 */
class AgentscopeSkillMaterializationGatewayTest {

    @Test
    public void materializeProducesDiscoverableSkillDir() throws Exception {
        AiRuntimeProperties properties = new AiRuntimeProperties();
        Path root = Files.createTempDirectory("nexai-skill-test");
        properties.getSkills().setRoot(root.toString());

        AgentscopeSkillMaterializationGateway gateway =
                new AgentscopeSkillMaterializationGateway(properties);

        Skill skill = Skill.create("data-clean", "数据清洗能力包", SkillOwnerLevel.TENANT, null);
        SkillContent content = SkillContent.of(
                "---\nname: data-clean\ndescription: 数据清洗能力包\n---\n# 数据清洗\n\n清洗规则",
                Map.of("references/rules.md", "# 规则", "scripts/clean.py", "print('clean')"));

        String dir = gateway.materialize(skill, 100L, content);

        // 物化位置按归属层级：{root}/t{tenantId}/skills/{name}/
        Path expected = root.resolve("t100").resolve("skills").resolve("data-clean");
        assertEquals(expected.toString(), dir);
        assertTrue(Files.isDirectory(expected), "物化目录应存在：" + expected);

        // SKILL.md 落盘（agentscope 契约：YAML frontmatter + 内容）
        Path skillMd = expected.resolve("SKILL.md");
        assertTrue(Files.exists(skillMd), "SKILL.md 应物化");
        String markdown = Files.readString(skillMd);
        assertTrue(markdown.contains("name: data-clean"), "SKILL.md 应含 name frontmatter");
        assertTrue(markdown.contains("description: 数据清洗能力包"), "SKILL.md 应含 description frontmatter");

        // 资源文件落盘
        assertTrue(Files.exists(expected.resolve("references/rules.md")), "资源文件应物化");
        assertTrue(Files.exists(expected.resolve("scripts/clean.py")), "脚本资源应物化");
        assertEquals("# 规则", Files.readString(expected.resolve("references/rules.md")));

        // agentscope 文件仓库源可发现（getAllSkillNames 读到该 skill）
        io.agentscope.core.skill.repository.FileSystemSkillRepository repo =
                new io.agentscope.core.skill.repository.FileSystemSkillRepository(
                        expected.getParent(), false);
        assertTrue(repo.getAllSkillNames().contains("data-clean"),
                "agentscope 文件仓库源应能发现物化的 skill");
        var agentSkill = repo.getSkill("data-clean");
        assertTrue(agentSkill != null && "数据清洗能力包".equals(agentSkill.getDescription()),
                "agentscope 解析出的 skill 描述应与 DB 一致");
    }

    @Test
    public void materializeIsIdempotent() throws Exception {
        AiRuntimeProperties properties = new AiRuntimeProperties();
        Path root = Files.createTempDirectory("nexai-skill-test-cache");
        properties.getSkills().setRoot(root.toString());

        AgentscopeSkillMaterializationGateway gateway =
                new AgentscopeSkillMaterializationGateway(properties);

        Skill skill = Skill.create("cache-skill", "缓存能力包", SkillOwnerLevel.TENANT, null);
        SkillContent content = SkillContent.of(
                "---\nname: cache-skill\ndescription: 缓存\n---\n# 缓存", null);

        // 两次物化：内容一致 → 结果幂等（文件内容不变；mtime 精度不足以断言跳过，断言内容与可发现性）
        gateway.materialize(skill, 200L, content);
        String dir = gateway.materialize(skill, 200L, content);

        Path skillMd = Path.of(dir).resolve("SKILL.md");
        assertTrue(Files.exists(skillMd));
        io.agentscope.core.skill.repository.FileSystemSkillRepository repo =
                new io.agentscope.core.skill.repository.FileSystemSkillRepository(
                        Path.of(dir).getParent(), false);
        assertTrue(repo.getAllSkillNames().contains("cache-skill"), "重复物化后仍可被仓库发现");
    }

}
