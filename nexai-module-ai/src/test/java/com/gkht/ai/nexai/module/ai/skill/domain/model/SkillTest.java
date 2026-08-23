package com.gkht.ai.nexai.module.ai.skill.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Skill 聚合纯 JUnit：创建校验、版本链登记、内容值对象校验。
 */
class SkillTest {

    @Test
    @DisplayName("创建租户级 skill：名称描述规范化，无版本指针")
    void createTenantSkill() {
        Skill skill = Skill.create("data-clean", "数据清洗能力包", SkillOwnerLevel.TENANT, null);
        assertEquals("data-clean", skill.getName());
        assertEquals("数据清洗能力包", skill.getDescription());
        assertEquals(SkillOwnerLevel.TENANT, skill.getOwnerLevel());
        assertTrue(!skill.hasVersion());
    }

    @Test
    @DisplayName("创建用户级 skill：必须携带归属用户")
    void createUserSkillRequiresOwner() {
        assertThrows(IllegalArgumentException.class,
                () -> Skill.create("my-skill", "用户技能", SkillOwnerLevel.USER, null));
        Skill skill = Skill.create("my-skill", "用户技能", SkillOwnerLevel.USER, 42L);
        assertEquals(42L, skill.getOwnerUserId());
    }

    @Test
    @DisplayName("登记版本：版本号推进当前指针，内容不可变")
    void addVersionAdvancesPointer() {
        Skill skill = Skill.create("data-clean", "数据清洗能力包", SkillOwnerLevel.TENANT, null);
        SkillContent v1 = SkillContent.of("---\nname: data-clean\ndescription: 清洗\n---\n# 清洗", null);
        SkillVersion version1 = skill.addVersion(1, v1, "首版");
        assertEquals(1, version1.getVersionNo());
        assertEquals(1, skill.getCurrentVersionNo());

        SkillContent v2 = SkillContent.of("---\nname: data-clean\ndescription: 清洗 v2\n---\n# 清洗 v2",
                Map.of("rules.md", "rule1"));
        SkillVersion version2 = skill.addVersion(2, v2, "补充规则");
        assertEquals(2, version2.getVersionNo());
        assertEquals(2, skill.getCurrentVersionNo());
        // 版本内容各自不可变
        assertEquals("---\nname: data-clean\ndescription: 清洗\n---\n# 清洗",
                skill.addVersion(3, v1, "回退").getContent().getMarkdown());
    }

    @Test
    @DisplayName("内容校验：空 Markdown 拒绝，资源路径逃逸拒绝")
    void contentValidation() {
        assertThrows(IllegalArgumentException.class, () -> SkillContent.of("  ", null));
        assertThrows(IllegalArgumentException.class,
                () -> SkillContent.of("---\nname: x\ndescription: y\n---\n内容",
                        Map.of("../evil.txt", "x")));
        assertThrows(IllegalArgumentException.class,
                () -> SkillContent.of("---\nname: x\ndescription: y\n---\n内容",
                        Map.of("/abs.txt", "x")));
    }

}
